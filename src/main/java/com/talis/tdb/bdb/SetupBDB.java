/*
 * (c) Copyright 2009 Hewlett-Packard Development Company, LP
 * All rights reserved.
 * [See end of file]
 */

package com.talis.tdb.bdb ;

import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import atlas.lib.ColumnMap ;
import atlas.lib.StrUtils ;

import com.hp.hpl.jena.tdb.TDBException ;
import com.hp.hpl.jena.tdb.base.file.Location ;
import com.hp.hpl.jena.tdb.base.record.RecordFactory ;
import com.hp.hpl.jena.tdb.graph.DatasetPrefixStorage ;
import com.hp.hpl.jena.tdb.index.Index ;
import com.hp.hpl.jena.tdb.index.RangeIndex ;
import com.hp.hpl.jena.tdb.index.TupleIndex ;
import com.hp.hpl.jena.tdb.index.TupleIndexRecord ;
import com.hp.hpl.jena.tdb.nodetable.NodeTable ;
import com.hp.hpl.jena.tdb.nodetable.NodeTableCache ;
import com.hp.hpl.jena.tdb.nodetable.NodeTableInline ;
import com.hp.hpl.jena.tdb.store.DatasetGraphTDB ;
import com.hp.hpl.jena.tdb.store.DatasetPrefixesTDB ;
import com.hp.hpl.jena.tdb.store.NodeId ;
import com.hp.hpl.jena.tdb.store.QuadTable ;
import com.hp.hpl.jena.tdb.store.TripleTable ;
import com.hp.hpl.jena.tdb.sys.Names ;
import com.hp.hpl.jena.tdb.sys.SetupTDB ;
import com.hp.hpl.jena.tdb.sys.SystemTDB ;

/** Makes things: BDB-JE backed */

public class SetupBDB
{
    private static Logger log = LoggerFactory.getLogger(SetupBDB.class) ;
    
    public static DatasetGraphTDB buildDataset(BDBinstance config)
    {
        log.info("Berkeley DB Java Edition") ;
        // -- Node Table.
        Location location = config.getLocation() ;
        
        NodeTable nodeTable = makeNodeTable(config, Names.indexNode2Id, Names.indexId2Node) ;
        // Correct size?
        nodeTable = NodeTableCache.create(nodeTable, SystemTDB.Node2NodeIdCacheSize, SystemTDB.NodeId2NodeCacheSize) ; 
        nodeTable = NodeTableInline.create(nodeTable) ;
        
        TripleTable tripleTable = makeTripleTable(config, nodeTable, 
                                                  Names.primaryIndexTriples, Names.tripleIndexes) ;
        QuadTable quadTable = makeQuadTable(config, nodeTable,
                                            Names.primaryIndexQuads, Names.quadIndexes) ;

        DatasetPrefixStorage prefixes = makePrefixes(config) ;

        // ---- Create the DatasetGraph object
        DatasetGraphTDB dsg = new DatasetGraphTDB(tripleTable, quadTable, prefixes, SetupTDB.chooseOptimizer(location), location, null) ;
        return dsg ;
    }

    public static TripleTable makeTripleTable(BDBinstance config, NodeTable nodeTable, 
                                              String dftPrimary, String[] dftIndexes)
    {
        log.debug("Triple table: "+dftPrimary+" :: "+StrUtils.join(",", dftIndexes)) ;
        TupleIndex tripleIndexes[] = makeTupleIndexes(config, dftPrimary, dftIndexes) ;
        if ( tripleIndexes.length != dftIndexes.length )
            SetupBDB.error(log, "Wrong number of triple table tuples indexes: "+tripleIndexes.length) ;
        TripleTable tripleTable = new TripleTable(tripleIndexes, nodeTable) ;
        return tripleTable ;
    }
    
    public static QuadTable makeQuadTable(BDBinstance config, NodeTable nodeTable, String dftPrimary, String[] dftIndexes)
    {
        log.debug("Quad table: "+dftPrimary+" :: "+StrUtils.join(",", dftIndexes)) ;
        
        TupleIndex quadIndexes[] = makeTupleIndexes(config, dftPrimary, dftIndexes) ;
        if ( quadIndexes.length != dftIndexes.length )
            SetupBDB.error(log, "Wrong number of triple table tuples indexes: "+quadIndexes.length) ;
        QuadTable quadTable = new QuadTable(quadIndexes, nodeTable) ;
        return quadTable ;
    }


    public static DatasetPrefixStorage makePrefixes(BDBinstance config)
    {
        log.debug("Prefixes") ;

        String primary = Names.primaryIndexPrefix ;
        String indexes[] = Names.prefixIndexes ;
        
        TupleIndex prefixIndexes[] = makeTupleIndexes(config, primary, indexes) ;
        if ( prefixIndexes.length != indexes.length )
            SetupBDB.error(log, "Wrong number of triple table tuples indexes: "+prefixIndexes.length) ;
        
        NodeTable prefixNodes = makeNodeTable(config, Names.prefixNode2Id, Names.prefixId2Node)  ;
        
        DatasetPrefixesTDB prefixes = new DatasetPrefixesTDB(prefixIndexes, prefixNodes) ; 
        
        
        return prefixes ;
    }

    public static TupleIndex[] makeTupleIndexes(BDBinstance config, String primary, String[] descs)
    {
        if ( primary.length() != 3 && primary.length() != 4 )
            SetupBDB.error(log, "Bad primary key length: "+primary.length()) ;

        int indexRecordLen = primary.length()*NodeId.SIZE ;
        TupleIndex indexes[] = new TupleIndex[descs.length] ;
        for (int i = 0 ; i < indexes.length ; i++)
            indexes[i] = makeTupleIndex(config, primary, descs[i], indexRecordLen) ;
        return indexes ;
    }
    
    private static TupleIndex makeTupleIndex(BDBinstance config,
                                             String primary, String indexOrder,
                                             int keyLength)
    {
        // Value part is null (zero length)
        String indexName = indexOrder ;
        RangeIndex rIndex = makeRangeIndex(config, indexName, keyLength, 0) ;
        TupleIndex tupleIndex = new TupleIndexRecord(primary.length(), new ColumnMap(primary, indexOrder), rIndex.getRecordFactory(), rIndex) ;
        return tupleIndex ;
    }
    
    private static Index makeIndex(BDBinstance config, String indexName, 
                                   int dftKeyLength, int dftValueLength)
    {
        return makeRangeIndex(config, indexName, dftKeyLength, dftValueLength) ;
    }
    
    private static RangeIndex makeRangeIndex(BDBinstance config, String indexName, 
                                             int dftKeyLength, int dftValueLength)
    {
        RangeIndex rIndex = new RangeIndexBDB(indexName, config, makeRecordFactory(indexName, dftKeyLength, dftValueLength)) ;
        return rIndex ;
    }

    public static RecordFactory makeRecordFactory(String keyName, int keyLen, int valLen)
    {
        return new RecordFactory(keyLen, valLen) ;
    }
    
    public static NodeTable makeNodeTable(BDBinstance config, 
                                          String indexNode2Id,
                                          String indexId2Node )
    {
        return new NodeTableBDB(config, indexNode2Id, indexId2Node) ;
    }

    public static void error(Logger log, String msg)
    {
        if ( log != null )
            log.error(msg) ;
        throw new TDBException(msg) ;
    }
}
/*
 * (c) Copyright 2009 Hewlett-Packard Development Company, LP All rights
 * reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer. 2. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. 3. The name of the author may not
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */