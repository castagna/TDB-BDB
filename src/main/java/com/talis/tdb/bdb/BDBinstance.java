/*
 * (c) Copyright 2009 Hewlett-Packard Development Company, LP
 * All rights reserved.
 * [See end of file]
 */

package com.talis.tdb.bdb;

import java.io.File ;
import java.io.IOException ;
import java.util.Properties ;

import org.openjena.atlas.lib.FileOps ;
import org.openjena.atlas.lib.PropertyUtils ;

import com.hp.hpl.jena.tdb.TDBException ;
import com.hp.hpl.jena.tdb.base.file.Location ;
import com.sleepycat.je.* ;

public class BDBinstance
{
    private Location location ;
    private Environment dbEnv = null;
    private DatabaseConfig dbConfig = null ;
    private LockMode lockMode = LockMode.DEFAULT ;
    private CursorConfig cursorConfig = CursorConfig.DEFAULT ;
    
    public static int BDB_cacheSizePercent = 75 ;
    
    private boolean closed = false ;
    
    public static void enable()
    {
        // and disable normal graphs
    }
    
    private static final String jeProperties = "je.properties" ; 
    
    public BDBinstance(String dirname)
    {
        location = new Location(dirname) ;
        FileOps.ensureDir(dirname) ;
        
        try { 
            /*
             * This file must be placed in your environment home directory
             */
            /* (this = je.properties)
            """
            The parameters set in this file take precedence over the configuration behavior
            coded into the JE application by your application developers.
            """
            */ 
            
            EnvironmentConfig envConfig ;
            
            if ( location.exists(jeProperties) )
            {
                try
                {
                    Properties properties = PropertyUtils.loadFromFile(location.getPath(jeProperties)) ;
                    envConfig = new EnvironmentConfig(properties);
                } catch (IOException ex)
                { throw new TDBException(ex) ; }
            }
            else
            {
                envConfig = new EnvironmentConfig() ;
                envConfig.setCachePercent(BDB_cacheSizePercent) ;
                // Overrides CHECKPOINTER_WAKEUP_INTERVAL
                envConfig.setConfigParam(EnvironmentConfig.CHECKPOINTER_BYTES_INTERVAL,
                                         Long.toString(20*1024*1024));
                // Default is "off" and using only the log file size.  Seems iffy.
                // Set to 30s.
                envConfig.setConfigParam(EnvironmentConfig.CHECKPOINTER_WAKEUP_INTERVAL,
                                         Long.toString(30*1000*1000)) ;
            }
            // ---- Set some basic configuration things.
            envConfig.setAllowCreate(true);
            
            dbEnv = new Environment(new File(dirname), envConfig);
            dbConfig = new DatabaseConfig() ;
            dbConfig.setAllowCreate(true) ;
            dbConfig.setDeferredWrite(true) ;
            dbConfig.setTransactional(false) ;  // Default
        }
        catch (DatabaseException ex)
        {
            throw new TDBException("SetupBDB",ex) ;
        }     
    }

    public Database openDatabase(String name)
    {
        Transaction txn = null ;
        Database db ;
        try { return getDbEnv().openDatabase(txn, name, getDbConfig()) ; }
        catch (DatabaseException ex) { throw new TDBException(ex) ; }
    }
    
    public void close()
    {
        if ( ! closed )
        {
            closed = true ;
            try { 
                dbEnv.cleanLog();
                dbEnv.close();
            }
            catch (DatabaseException ex)
            {
                throw new TDBException("SetupBDB.close",ex) ;
            }
        }
    }

    Location getLocation()
    {
        return location ;
    }

    private Environment getDbEnv()
    {
        return dbEnv ;
    }

    DatabaseConfig getDbConfig()
    {
        return dbConfig ;
    }

    LockMode getLockMode()
    {
        return lockMode ;
    }

    CursorConfig getCursorConfig()
    {
        return cursorConfig ;
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