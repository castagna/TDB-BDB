/*
 * (c) Copyright 2010 Talis Systems Ltd.
 * All rights reserved.
 * [See end of file]
 */

package com.talis.tdb.bdb;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.openjena.atlas.junit.BaseTest;
import org.openjena.atlas.lib.FileOps;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.sparql.core.assembler.AssemblerUtils;
import com.hp.hpl.jena.tdb.store.DatasetGraphTDB;

public class TestTDB_BDB extends BaseTest
{
    static final String BDB_DIR = "testing/BDB" ;
    
    @Before public void setup() 
    {
        FileOps.ensureDir(BDB_DIR) ;
        FileOps.clearDirectory(BDB_DIR) ;
    }
    
    @AfterClass public static void cleanup()
    {
        FileOps.clearDirectory(BDB_DIR) ;
    }
    
    @Test public void setup_01()
    {
        BDBinstance config = new BDBinstance(BDB_DIR) ;
        DatasetGraphTDB dsg = SetupBDB.buildDataset(config) ;
        dsg.close();
        config.close();
    }
    
    //TDBMaker.releaseDataset(dsg) ;
    @Test public void setup_02()
    {
        BDBinstance config = new BDBinstance(BDB_DIR) ;
        DatasetGraphTDB dsg = SetupBDB.buildDataset(config) ;
        dsg.close();
        config.close();
    }
    
    @Test public void assemble_1()
    {
        String assemblerFile = "testing/tdb_bdb.ttl" ;
        Dataset ds = (Dataset)AssemblerUtils.build(assemblerFile, DatasetAssemblerTDB_BDB.tDatasetTDB_BDB) ;
        assertTrue(ds.asDatasetGraph() instanceof DatasetGraphTDBBDB) ;
        ds.close() ;
    }

    // and again
    @Test public void assemble_2()
    {
        String assemblerFile = "testing/tdb_bdb.ttl" ;
        Dataset ds = (Dataset)AssemblerUtils.build(assemblerFile, DatasetAssemblerTDB_BDB.tDatasetTDB_BDB) ;
        assertTrue(ds.asDatasetGraph() instanceof DatasetGraphTDBBDB) ;
        ds.close() ;
    }

}

/*
 * (c) Copyright 2010 Talis Systems Ltd.
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