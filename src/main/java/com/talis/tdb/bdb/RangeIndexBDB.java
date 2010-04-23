/*
 * (c) Copyright 2009 Hewlett-Packard Development Company, LP
 * All rights reserved.
 * [See end of file]
 */

package com.talis.tdb.bdb;

import java.util.Iterator ;
import java.util.NoSuchElementException ;


import org.openjena.atlas.iterator.NullIterator ;
import org.openjena.atlas.lib.Bytes ;
import org.openjena.atlas.lib.Closeable ;

import com.hp.hpl.jena.shared.JenaException ;
import com.hp.hpl.jena.tdb.TDBException ;
import com.hp.hpl.jena.tdb.base.record.Record ;
import com.hp.hpl.jena.tdb.base.record.RecordFactory ;
import com.hp.hpl.jena.tdb.index.RangeIndex ;
import com.sleepycat.je.Cursor ;
import com.sleepycat.je.Database ;
import com.sleepycat.je.DatabaseEntry ;
import com.sleepycat.je.DatabaseException ;
import com.sleepycat.je.OperationStatus ;
import com.sleepycat.je.Transaction ;

public class RangeIndexBDB implements RangeIndex
{
    
    //private static Logger log = LoggerFactory.getLogger(RangeIndexBDB.class) ;
    protected RecordFactory recordFactory ;
    protected Database db ;
    protected BDBinstance setup ;
    protected Transaction txn = null ;
    private final String indexName ;
    
    public RangeIndexBDB(String name, BDBinstance setup, RecordFactory recordFactory)
    {
        indexName = name ;
        this.recordFactory = recordFactory ;
        this.setup = setup ;
        db = setup.openDatabase(name) ;
    }
    
    @Override
    public RecordFactory getRecordFactory()
    {
        return recordFactory  ;
    }

    @Override
    public boolean add(Record record)
    {
        try {
            DatabaseEntry entry = entryKey(record) ;
            DatabaseEntry value = entryValue(record) ;
            
            OperationStatus status = db.putNoOverwrite(txn, entry, value) ;
            if ( status == OperationStatus.KEYEXIST )
                // Duplicate
                return false ;
            return true ;
        } catch (DatabaseException ex)
        {
            throw new TDBException("IndexBDB",ex) ;
        }
    }

    @Override
    public void close()
    {
        try {
            db.close() ;
        } catch (DatabaseException ex)
        {
            //throw new TDBException("IndexBDB",ex) ;
            System.err.println(ex) ;
        }
        catch (IllegalStateException ex)
        {
            
        }
    }

    @Override
    public boolean contains(Record record)
    {
        return find(record) != null ;
    }

    @Override
    public boolean delete(Record record)
    {
        try {
            DatabaseEntry key = entryKey(record) ;
            OperationStatus status = db.delete(txn, key) ;
            if ( status == OperationStatus.NOTFOUND )
                return false ;
            return true ;
        } catch (DatabaseException ex)
        {
            throw new TDBException("IndexBDB",ex) ;
        }
    }

    @Override
    public Record find(Record record)
    {
        try {
            DatabaseEntry key = entryKey(record) ;
            DatabaseEntry retValue = new DatabaseEntry() ;
            OperationStatus status = db.get(txn, key, retValue, setup.getLockMode()) ;
            if ( status == OperationStatus.NOTFOUND )
                return null ;
            return record(key, retValue) ;
        } catch (DatabaseException ex)
        {
            throw new TDBException("IndexBDB",ex) ;
        }
    }

    @Override
    public boolean isEmpty()
    {
        // Can't tell.
        return false ;
    }

    @Override
    public Iterator<Record> iterator()
    {
        try {
            Cursor cursor = db.openCursor(txn, setup.getCursorConfig());
            return new IteratorRangeBDB(cursor, null, null, null) ;     // No start, no end.
        } catch (DatabaseException dbe)
        { throw new JenaException("RangeIndexBDB", dbe) ; }
    }

    @Override
    public Iterator<Record> iterator(Record recordMin, Record recordMax)
    {
        try {
            Cursor cursor = db.openCursor(txn, setup.getCursorConfig());
            DatabaseEntry key = entryKey(recordMin) ;
            DatabaseEntry data = genBlank() ;
            DatabaseEntry end = entryKey(recordMax) ;
            OperationStatus status = cursor.getSearchKeyRange(key, data, setup.getLockMode()) ;
            if ( status != OperationStatus.SUCCESS )
                return new NullIterator<Record>() ;
            return new IteratorRangeBDB(cursor, key, data, end) ;
        } catch (DatabaseException dbe)
        { throw new JenaException("RangeIndexBDB", dbe) ; }
    }
    
    class IteratorRangeBDB implements Iterator<Record>, Closeable
    {
        private Cursor cursor ;
        private DatabaseEntry end ;
        private boolean finished = false ;
        private Record slot = null ;

        // End is exclusive.
        IteratorRangeBDB(Cursor cursor, DatabaseEntry firstKey, DatabaseEntry firstValue, DatabaseEntry end) 
        {
            this.cursor = cursor ;
            this.end = end ;
            // This was a positioned cursor. 
            if ( firstKey != null )
                this.slot = record(firstKey, firstValue) ;
        }
        
        public void close()
        {
            try
            {
                if ( ! finished ) 
                    endIterator() ;
            } catch (DatabaseException ex)
            {
                ex.printStackTrace();
            }
        }
        
        @Override
        public boolean hasNext()
        {
            if ( finished ) return false ;
            if ( slot != null ) return true ;
            
            try {
                DatabaseEntry key = new DatabaseEntry(new byte[recordFactory.recordLength()]) ;
                DatabaseEntry value = genBlank() ;
                OperationStatus status = cursor.getNext(key, value, null) ;
                if ( status == OperationStatus.NOTFOUND )
                    return endIterator() ;
                    
                if ( status != OperationStatus.SUCCESS )
                    throw new JenaException("GraphBDB.Mapper: cursor get failed") ;
                // Compare.
                if ( end != null && compare(key, end) >= 0) 
                    return endIterator() ; 
                slot = record(key, value) ;
                return true ;
            } catch (DatabaseException dbe)
            { throw new JenaException("GraphBDB.Mapper", dbe) ; }
        }
        
        private boolean endIterator() throws DatabaseException 
        {
            cursor.close() ;
            finished = true ;
            return false ;
        }
        
        // Use DB comparator instead?
        private int compare(DatabaseEntry x, DatabaseEntry y)
        {
            byte[] xBytes = x.getData() ;
            byte[] yBytes = y.getData() ;
            
            for ( int i = 0 ; i < xBytes.length ; i++ )
            {
                byte b1 = xBytes[i] ;
                byte b2 = yBytes[i] ;
                if ( b1 == b2 )
                    continue ;
                // Treat as unsigned values in the bytes. 
                return (b1&0xFF) - (b2&0xFF) ;  
            }
            return  0 ;
        }

        @Override
        public Record next() {
            if ( ! hasNext() )
                throw new NoSuchElementException() ;
            Record x = slot ;
            slot = null ;
            return x ;
        }

        @Override
        public void remove() { throw new UnsupportedOperationException("remove") ; }
    }

    @Override
    public Record minKey()
    {
        // Use cursor getFirst() and cursor.getLast()
        // Do via find smallest possible key.
        throw new UnsupportedOperationException("minKey") ;
    }

    @Override
    public Record maxKey()
    {
        // Do via find largest possible key.
        throw new UnsupportedOperationException("maxKey") ;
    }

    @Override
    public void sync() { sync(true) ; }
    
    @Override
    public void sync(boolean force)
    {
        try
        {
            db.sync() ;
        } catch (DatabaseException ex)
        {
            throw new TDBException(ex) ; }
    } 
    
    @Override
    public void check()
    {}

    @Override
    public long size()
    {
        return -1 ;
    }

    @Override
    public long sessionTripleCount()
    {
        return Integer.MIN_VALUE ;
    }

    // Don't use in a .get() or .getNext()
    private static DatabaseEntry empty = new DatabaseEntry(new byte[0]) ;
    protected static DatabaseEntry genBlank() { return new DatabaseEntry() ; }
    
    protected DatabaseEntry entryKey(Record record)
    {
        byte b[] = Bytes.copyOf(record.getKey()) ;
        return new DatabaseEntry(b) ;
    }
    
    protected DatabaseEntry entryValue(Record record)
    {
        if ( ! recordFactory.hasValue() )
            return new DatabaseEntry(new byte[0]) ;
        byte b[] = new byte[recordFactory.valueLength()] ;
        System.arraycopy(b, 0, record.getValue(), 0, recordFactory.valueLength()) ;
        return new DatabaseEntry(b) ;
    }
    
    protected Record record(DatabaseEntry key, DatabaseEntry value)
    {
        if ( false ) return recordFactory.create(key.getData(), value.getData()) ;
        
        // Avoid copy if key-only?
        byte k[] = new byte[recordFactory.keyLength()] ;
        System.arraycopy(key.getData(), 0, k, 0, recordFactory.keyLength()) ;
        
        byte v[] = null ;
        if ( recordFactory.hasValue() )
        {
            v = new byte[recordFactory.valueLength()] ;
            System.arraycopy(value.getData(), 0, v, 0, recordFactory.valueLength()) ;
        }
        return recordFactory.create(k, v) ;
    }
}

/*
 * (c) Copyright 2009 Hewlett-Packard Development Company, LP
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */