package tdbbdb;

import java.io.FileInputStream;

import com.hp.hpl.jena.tdb.store.DatasetGraphTDB;
import com.hp.hpl.jena.tdb.store.bulkloader.BulkLoader;
import com.talis.tdb.bdb.BDBinstance;
import com.talis.tdb.bdb.SetupBDB;

public class load {

    public static void main(String ... args) throws Exception {
    	if ( args.length != 2 ) {
    		System.err.println ("Usage: tdb_bdb.Load filename.nt path");
    		System.exit(-1);
    	}
        BDBinstance config = new BDBinstance(args[1]);
        DatasetGraphTDB dsg = SetupBDB.buildDataset(config);
        BulkLoader.loadDefaultGraph(dsg, new FileInputStream(args[0]), true);
        dsg.sync(true) ;
        dsg.close() ;
    }
    
	
}
