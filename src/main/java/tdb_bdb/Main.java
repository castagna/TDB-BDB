/*
 * (c) Copyright 2009 Talis Information Ltd.
 * All rights reserved.
 * [See end of file]
 */

package tdb_bdb;

import java.util.ArrayList ;
import java.util.Collections ;
import java.util.List ;
import java.util.Map ;

import org.openjena.atlas.lib.FileOps ;
import org.openjena.atlas.logging.Log ;

import com.hp.hpl.jena.query.Dataset ;
import com.hp.hpl.jena.query.Query ;
import com.hp.hpl.jena.query.QueryExecution ;
import com.hp.hpl.jena.query.QueryExecutionFactory ;
import com.hp.hpl.jena.query.QueryFactory ;
import com.hp.hpl.jena.query.ResultSet ;
import com.hp.hpl.jena.query.ResultSetFormatter ;
import com.hp.hpl.jena.rdf.model.Model ;
import com.hp.hpl.jena.sparql.core.DatasetImpl ;
import com.hp.hpl.jena.sparql.util.Timer ;
import com.hp.hpl.jena.tdb.store.DatasetGraphTDB ;
import com.hp.hpl.jena.util.FileManager ;
import com.sleepycat.je.config.ConfigParam ;
import com.sleepycat.je.config.EnvironmentParams ;
import com.talis.tdb.bdb.BDBinstance ;
import com.talis.tdb.bdb.SetupBDB ;

public class Main
{
    static { Log.setLog4j() ; }

    public static void main(String ... args) throws Exception
    {
        BDB_defaults() ;

        // Sort out - or ignore - open cursors on close.
        // Prefixes is a partial scan.
        boolean build  = true ;

        if ( build )
        {
            FileOps.ensureDir("BDB") ;
            FileOps.clearDirectory("BDB") ;
        }

        BDBinstance config = new BDBinstance("BDB") ;
        DatasetGraphTDB dsg = SetupBDB.buildDataset(config) ;
        Dataset ds = new DatasetImpl(dsg) ;
        
        if ( build )
        {
            //        Graph graph = dsg.getDefaultGraph() ;
            //        Triple t = SSE.parseTriple("(<s> <p> <o>)") ;
            //        graph.add(t) ;
            //BulkLoader.load((GraphTDB)graph, "D.ttl", true) ;
            //      dsg.sync(true) ;
            //      dsg.close() ;

            //FileManager.get().readModel(ds.getDefaultModel(), "D.ttl") ;
            ds.getDefaultModel().read("file:D.ttl", "TTL") ;

            Model m = FileManager.get().loadModel("D.ttl") ;
            System.out.println(m.size()) ;

            ds.close() ;
            System.out.println("Exit") ;
            System.exit(0) ;
        }

        if ( false )
            FileManager.get().readModel(ds.getDefaultModel(), "D.ttl") ;

        Query query = QueryFactory.read("Q.arq") ;

        Timer timer = new Timer() ;

        for ( int i = 0 ; i < 3 ; i++ )
        {
            timer.startTimer() ;
            query(query, ds) ;
            long t = timer.endTimer() ;
            System.out.printf("Time: %.2fs\n", t/1000.0) ; 
        }
        ds.close() ;
        System.exit(0) ;
    }
    
    public static void BDB_defaults()
    {
        Map<String, ConfigParam> x = EnvironmentParams.SUPPORTED_PARAMS ;
        
        List<String> keys = new ArrayList<String>() ;
        keys.addAll(x.keySet()) ;
        Collections.sort(keys) ;
        
        for (String k : keys )
        {
            System.out.print(k) ;
            System.out.print("=") ;
            System.out.println(x.get(k).getDefault()) ;
        }
    }


    
    private static void query(Query q, Dataset ds)
    {
        QueryExecution qexec = QueryExecutionFactory.create(q, ds) ;
        ResultSet rs = qexec.execSelect() ;
        ResultSetFormatter.out(rs) ;
        qexec.close() ;
    }
}

/*
 * (c) Copyright 2009 Talis Information Ltd.
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