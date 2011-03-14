package dev;

import java.io.File;
import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.openjena.atlas.lib.Bytes;
import org.openjena.atlas.lib.FileOps;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.tdb.TDBException;
import com.hp.hpl.jena.tdb.store.DatasetGraphTDB;
import com.hp.hpl.jena.tdb.store.bulkloader.BulkLoader;
import com.hp.hpl.jena.tdb.sys.Names;
import com.hp.hpl.jena.tdb.sys.SystemTDB;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.talis.tdb.bdb.BDBinstance;
import com.talis.tdb.bdb.SetupBDB;

public class Merge {

	private static String root = "target/bdb";
	
	private static void merge (List<String> locations, String location) {
		
		int n = locations.size();
		List<BDBinstance> configs = new ArrayList<BDBinstance>();
		List<Database> dbs = new ArrayList<Database>();
		List<Cursor> cs = new ArrayList<Cursor>();
		List<DatabaseEntry> ks = new ArrayList<DatabaseEntry>();
		List<DatabaseEntry> vs = new ArrayList<DatabaseEntry>();
		ArrayList<OperationStatus> oss = new ArrayList<OperationStatus>();
		
		for ( int i = 0; i < n; i++ ) {
			configs.add( new BDBinstance(locations.get(i)));
			dbs.add( configs.get(i).openDatabase(Names.indexId2Node));
			cs.add( dbs.get(i).openCursor(null, CursorConfig.DEFAULT));
			ks.add( new DatabaseEntry());
			vs.add( new DatabaseEntry());
			oss.add ( cs.get(i).getNext(ks.get(i), vs.get(i), LockMode.DEFAULT));
		}

		BDBinstance conf = new BDBinstance(location);
		Database db = conf.openDatabase(Names.indexId2Node);
		Cursor out = db.openCursor(null, CursorConfig.DEFAULT);
		
		while ( cs.size() > 0 ) {
			int min = min(cs, oss, ks, vs);
			
			try {
				System.out.println(min + " " + Bytes.getLong(ks.get(min).getData()) + ":" + new String(vs.get(min).getData(), "UTF-8"));
				
				out.put(ks.get(min), vs.get(min));

				System.out.println("oss size = " + oss.size());
				oss.remove(min);
				System.out.println("oss size = " + oss.size());
				
				
				System.out.println(min + " " + Bytes.getLong(ks.get(min).getData()) + ":" + new String(vs.get(min).getData(), "UTF-8"));
				oss.add(min, cs.get(min).getNext(ks.get(min), vs.get(min), LockMode.DEFAULT));
				System.out.println(min + " " + Bytes.getLong(ks.get(min).getData()) + ":" + new String(vs.get(min).getData(), "UTF-8"));
				System.out.println("oss size = " + oss.size());
				
				
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			

			
			
		}

		out.close();
		db.sync();
		db.close();
		conf.close();
		
		for ( int i = 0; i < n; i++ ) {
			dbs.get(i).close();
			configs.get(i).close();
		}
		
	}

	private static int min (List<Cursor> cs, List<OperationStatus> oss, List<DatabaseEntry> ks, List<DatabaseEntry> vs) {
		int min = 0;
		DatabaseEntry k = ks.get(0);
		for ( int i = 1; i < cs.size(); i++ ) {
			if ( oss.get(i) == OperationStatus.SUCCESS ) {
				int c = compare ( k, ks.get(i) );
				if ( c < 0 ) {
					k = ks.get(i);
					min = i;
				}
			} else {
				cs.get(i).close();
				cs.remove(i);
				oss.remove(i);
				ks.remove(i);
				vs.remove(i);
			}
		}
		return min;
	}

    public static void main(String ... args) throws Exception {
        FileOps.ensureDir(root) ;
        FileOps.clearDirectory(root) ;
        FileOps.ensureDir(root + File.separator + "one") ;
        FileOps.clearDirectory(root + File.separator + "one") ;
        FileOps.ensureDir(root + File.separator + "two") ;
        FileOps.clearDirectory(root + File.separator + "two") ;
        FileOps.ensureDir(root + File.separator + "three") ;
        FileOps.clearDirectory(root + File.separator + "three") ;        
    	
        BDBinstance configOne = new BDBinstance(root + File.separator + "one");
        DatasetGraphTDB dsg = SetupBDB.buildDataset(configOne);
        BulkLoader.loadDefaultGraph(dsg, new FileInputStream("one.nt"), true);
        dsg.sync(true) ;
        dsg.close() ;

        BDBinstance configTwo = new BDBinstance(root + File.separator + "two");
        dsg = SetupBDB.buildDataset(configTwo);
        BulkLoader.loadDefaultGraph(dsg, new FileInputStream("two.nt"), true);
        dsg.sync(true) ;
        dsg.close() ;
        
        List<String> locations = new ArrayList<String>();
        locations.add(root + File.separator + "one");
        locations.add(root + File.separator + "two");
        
        merge(locations, root + File.separator + "three");
        
        System.out.println("--------------");
        print (root + File.separator + "one");
        System.out.println("--------------");
        print (root + File.separator + "two");
        System.out.println("--------------");
        print (root + File.separator + "three");
        System.out.println("--------------");
        
//        
//        BDBinstance configThree = new BDBinstance(root + File.separator + "three");
//        
//        Cursor c1 = null;
//        Cursor c2 = null;
//        try {
//            Database one = configOne.openDatabase(Names.indexId2Node);
//            Database two = configTwo.openDatabase(Names.indexId2Node);
//            Database three = configThree.openDatabase(Names.indexId2Node);
//            
//            c1 = one.openCursor(null, CursorConfig.DEFAULT);
//            c2 = two.openCursor(null, CursorConfig.DEFAULT);
//            
//            DatabaseEntry k1 = new DatabaseEntry();
//            DatabaseEntry v1 = new DatabaseEntry();
//            DatabaseEntry k2 = new DatabaseEntry();
//            DatabaseEntry v2 = new DatabaseEntry();
//            
//            OperationStatus s1 = null;
//            OperationStatus s2 = null;
//            while ( ( ( s1 = c1.getNext(k1, v1, LockMode.DEFAULT) ) == OperationStatus.SUCCESS ) 
//            		&& ( ( s2 = c2.getNext(k2, v2, LockMode.DEFAULT) ) == OperationStatus.SUCCESS ) ) {
//            	
//            	int c = compare (k1, k2);
//            	if ( c < 0 ) {
//            		three.put(null, k1, v1);
//            		three.put(null, k2, v2);
//            	} else if ( c > 0 ) {
//            		three.put(null, k2, v2);
//            		three.put(null, k1, v1);
//            	} else {
//            		three.put(null, k1, v1);
//            	}
////                long k = Bytes.getLong(k1.getData()); 
////                String v = new String(v1.getData(), "UTF-8");
////                System.out.println("Key | Data : " + k + " | " +  v + "");
//            }
//            
//            if ( s1 == OperationStatus.SUCCESS ) {
//            	while ( ( ( s1 = c1.getNext(k1, v1, LockMode.DEFAULT) ) == OperationStatus.SUCCESS ) ) {
//            		three.put(null, k1, v1);
//            	}
//            } else if ( s2 == OperationStatus.SUCCESS ) {
//            	while ( ( ( s2 = c2.getNext(k2, v2, LockMode.DEFAULT) ) == OperationStatus.SUCCESS ) ) {
//            		three.put(null, k2, v2);
//            	}
//            }
//            
//            three.sync();
//            three.close();
//        } finally {
//        	if ( c1 != null ) c1.close();
//        	if ( c2 != null ) c2.close();
//        }
//        
//        
//        
//        System.out.println("--------------");
//        print (configOne);
//        System.out.println("--------------");
//        print (configTwo);
//        System.out.println("--------------");
//        print (configThree);
//        System.out.println("--------------");
    }

    public static void print (String location) throws UnsupportedEncodingException {
        Cursor c = null;
        try {
        	BDBinstance setup = new BDBinstance(location);
            Database db = setup.openDatabase(Names.indexId2Node);
            c = db.openCursor(null, CursorConfig.DEFAULT);
            DatabaseEntry k = new DatabaseEntry();
            DatabaseEntry v = new DatabaseEntry();
            while ( c.getNext(k, v, LockMode.DEFAULT) == OperationStatus.SUCCESS ) {
                long key = Bytes.getLong(k.getData()); 
                String value = new String(v.getData(), "UTF-8");
                System.out.println("Key | Data : " + key + " | " +  value + "");
            }
        } finally {
        	if ( c != null ) c.close();
        }
    	
    }
    
    private static int compare(DatabaseEntry x, DatabaseEntry y)
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

}
