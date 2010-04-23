/*
 * (c) Copyright 2009 Hewlett-Packard Development Company, LP
 * All rights reserved.
 * [See end of file]
 */

package com.talis.tdb.bdb;

import java.util.Iterator ;

import org.openjena.atlas.iterator.Iter ;
import org.openjena.atlas.iterator.IteratorInteger ;
import org.openjena.atlas.iterator.Transform ;
import org.openjena.atlas.lib.Bytes ;
import org.openjena.atlas.lib.Pair ;
import org.openjena.atlas.lib.StrUtils ;

import com.hp.hpl.jena.graph.Node ;
import com.hp.hpl.jena.tdb.TDBException ;
import com.hp.hpl.jena.tdb.nodetable.NodeTable ;
import com.hp.hpl.jena.tdb.nodetable.NodecLib ;
import com.hp.hpl.jena.tdb.store.NodeId ;
import com.hp.hpl.jena.tdb.sys.SystemTDB ;
import com.sleepycat.je.Database ;
import com.sleepycat.je.DatabaseEntry ;
import com.sleepycat.je.DatabaseException ;
import com.sleepycat.je.OperationStatus ;
import com.sleepycat.je.Sequence ;
import com.sleepycat.je.SequenceConfig ;
import com.sleepycat.je.Transaction ;

public final class NodeTableBDB implements NodeTable
{
    //private static Logger log = LoggerFactory.getLogger(NodeTableBDB.class) ;
    
    Database nodeToId ;     // String -> int
    Database idToNode ;     // int -> String
    
    private Transaction txn = null ;
    private Sequence idNumbering ;
    private BDBinstance setup ;
    
    // The node table is shared (triples, quads) so can get closed multiple times. 
    private boolean closed = false ;

    public NodeTableBDB(BDBinstance config, String node2id, String id2Node)
    {
        this.setup = config ;
        
        try {
            nodeToId = config.openDatabase(node2id);
            idToNode = config.openDatabase(id2Node); 
            SequenceConfig sequenceConfig = new SequenceConfig() ;
            sequenceConfig.setAllowCreate(true) ;
            idNumbering = idToNode.openSequence(txn, entryNode("seq"), sequenceConfig) ;
        } catch (DatabaseException ex)
        {
            throw new TDBBDBException(ex) ;
        }
    }
    
    /** Store the node in the node table (if not already present) and return the allocated Id. */
    public NodeId getAllocateNodeId(Node node)
    {
        NodeId x = accessIndex(node, true) ;
        // if ( log.isTraceEnabled() ) log.trace("getAllocateNodeId: "+node+" ==> "+x) ;
        return x ;
    }
    
    /** Look up node and return the NodeId - return NodeId.NodeDoesNotExist if not found */
    public NodeId getNodeIdForNode(Node node)
    {
        NodeId x = accessIndex(node, true) ;
        // if ( log.isTraceEnabled() ) log.trace("getNodeIdForNode: "+node+" ==> "+x) ;
        return x ;

    }
    
/** Look up node id and return the Node - return null if not found */
    public Node getNodeForNodeId(NodeId id)
    {
        try {
            DatabaseEntry k = new DatabaseEntry(longBuff(id.getId())) ;
            DatabaseEntry v = new DatabaseEntry() ;
    
            OperationStatus status = idToNode.get(txn, k, v, setup.getLockMode()) ;
    
            if ( status == OperationStatus.NOTFOUND )
            {
                //if ( log.isTraceEnabled() ) log.trace("getNodeForNodeId: "+id+" ==> Not found") ;
                return null ;
            }
            String s = new String(v.getData(), v.getOffset(), v.getSize(), "UTF-8") ;
            Node n = NodecLib.decode(s, null) ;
            
            //if ( log.isTraceEnabled() ) log.trace("getNodeForNodeId: "+id+" ==> "+n) ;
            
            return n ;
        } catch (Exception ex)
        { throw new TDBBDBException(ex) ; }
    }

    private static DatabaseEntry entryNode(String str)
    {
        byte[] s = StrUtils.asUTF8bytes(str) ;
        DatabaseEntry k = new DatabaseEntry(s);
        return k ;
    }

    private NodeId accessIndex(Node node, boolean create)
    {
        try {
            DatabaseEntry nodeEntry = entryNode(NodecLib.encode(node, null)) ;
            DatabaseEntry idEntry = new DatabaseEntry() ;
            OperationStatus status = nodeToId.get(txn, nodeEntry, idEntry, setup.getLockMode()) ;

            if ( status == OperationStatus.SUCCESS )
            {
                long x = Bytes.getLong(idEntry.getData()) ;
                return NodeId.create(x) ;
            }
            if ( ! create )
                return NodeId.NodeDoesNotExist ;
            
            long x = idNumbering.get(txn, 1) ;
            idEntry = new DatabaseEntry(longBuff(x));

            nodeToId.put(txn, nodeEntry, idEntry) ;
            idToNode.put(txn, idEntry, nodeEntry) ;

            NodeId nodeId = NodeId.create(x) ;
            return nodeId ;

        } catch (DatabaseException dbe) {
            throw new TDBBDBException(dbe) ;
        } 
    }

    private static byte[] longBuff(long id)
    {
        byte[] b = new byte[SystemTDB.SizeOfLong] ;
        Bytes.setLong(id, b) ;
        return b ;
    }

    @Override
    public Iterator<Pair<NodeId, Node>> all()
    {
        // A cursor would be fatser ... but more work!
        IteratorInteger iter = new IteratorInteger(0, Long.MAX_VALUE) ;
        Transform<Long, Pair<NodeId, Node>> transform = new Transform<Long, Pair<NodeId, Node>>() {
            public Pair<NodeId, Node> convert(Long item)
            {
                NodeId id = new NodeId(item.longValue()) ;
                Node n = getNodeForNodeId(id) ;
                return new Pair<NodeId, Node>(id, n) ;
            }
        } ;
        return Iter.map(iter, transform) ;
    }

    @Override
    public void close()
    {
        if ( closed )
            return ;
        closed = true ;
        try {
            idNumbering.close() ;
            nodeToId.close();
            idToNode.close();
        } catch (DatabaseException dbe) {
            throw new TDBBDBException(dbe) ;
        } 
    }

    @Override
    public void sync() { sync(true) ; }

    
    @Override
    public void sync(boolean force)
    {
     // BDB sync is only for deferred write (in-memory) DBs.
        try {
            nodeToId.sync();
            idToNode.sync();
        } catch (DatabaseException ex)
        { throw new TDBException(ex) ; }
    } 
    

    private static class TDBBDBException extends TDBException
    {
        TDBBDBException(Exception ex) { super(ex) ; }
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