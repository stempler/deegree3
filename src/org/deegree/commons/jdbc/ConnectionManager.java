//$HeadURL: svn+ssh://mschneider@svn.wald.intevation.org/deegree/base/trunk/resources/eclipse/files_template.xml $
/*----------------    FILE HEADER  ------------------------------------------
 This file is part of deegree.
 Copyright (C) 2001-2009 by:
 Department of Geography, University of Bonn
 http://www.giub.uni-bonn.de/deegree/
 lat/lon GmbH
 http://www.lat-lon.de

 This library is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 2.1 of the License, or (at your option) any later version.
 This library is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 Lesser General Public License for more details.
 You should have received a copy of the GNU Lesser General Public
 License along with this library; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 Contact:

 Andreas Poth
 lat/lon GmbH
 Aennchenstr. 19
 53177 Bonn
 Germany
 E-Mail: poth@lat-lon.de

 Prof. Dr. Klaus Greve
 Department of Geography
 University of Bonn
 Meckenheimer Allee 166
 53115 Bonn
 Germany
 E-Mail: greve@giub.uni-bonn.de
 ---------------------------------------------------------------------------*/

package org.deegree.commons.jdbc;

import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.deegree.commons.configuration.JDBCConnections;
import org.deegree.commons.configuration.PooledConnection;
import org.deegree.commons.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for accessing JDBC connections in deegree that are defined in configuration files.
 * <p>
 * Configuration of JDBC connections used in deegree is based on simple string identifiers: each configured JDBC
 * connection has a unique identifier. This class allows the retrieval of connections based on their identifier.
 * </p>
 * 
 * @author <a href="mailto:schneider@lat-lon.de">Markus Schneider</a>
 * @author last edited by: $Author: schneider $
 * 
 * @version $Revision: $, $Date: $
 */
public class ConnectionManager {

    private static Logger LOG = LoggerFactory.getLogger( ConnectionManager.class );

    private static Map<String, ConnectionPool> idToPools;

    /**
     * Initializes the {@link ConnectionManager}. This method must only be called once.
     * 
     * @param jaxbConns
     * @throws SQLException
     */
    public synchronized static void init( JDBCConnections jaxbConns )
                            throws SQLException {
        if ( idToPools != null ) {
            throw new SQLException( Messages.getMessage( "JDBC_MANAGER_ALREADY_INITIALIZED" ) );
        }
        idToPools = new HashMap<String, ConnectionPool>();
        addConnections( jaxbConns );
    }

    /**
     * Initializes the {@link ConnectionManager}. This method must only be called once.
     * 
     * @param jdbcConfigUrl
     * @throws JAXBException
     * @throws SQLException
     */
    public synchronized static void init( URL jdbcConfigUrl )
                            throws JAXBException, SQLException {
        JAXBContext jc = JAXBContext.newInstance( "org.deegree.commons.configuration" );
        Unmarshaller u = jc.createUnmarshaller();
        init ((JDBCConnections) u.unmarshal( jdbcConfigUrl ));
    }

    /**
     * Returns a connection from the connection pool with the given id.
     * 
     * @param id
     *            id of the connection pool
     * @return connection from the corresponding connection pool
     * @throws SQLException
     *             if the connection pool is unknown or a SQLException occurs creating the connection
     */
    public static Connection getConnection( String id )
                            throws SQLException {
        ConnectionPool pool = idToPools.get( id );
        if ( pool == null ) {
            throw new SQLException( Messages.getMessage( "JDBC_UNKNOWN_CONNECTION", id ) );
        }
        return pool.getConnection();
    }

    private static void addConnections( JDBCConnections jaxbConns ) {
        for ( PooledConnection jaxbConn : jaxbConns.getPooledConnection() ) {
            addConnection( jaxbConn );
        }
    }

    private static void addConnection( PooledConnection jaxbConn ) {
        String id = jaxbConn.getId();
        String url = jaxbConn.getUrl();
        String driver = jaxbConn.getDriver();
        String user = jaxbConn.getUser();
        String password = jaxbConn.getPassword();
        int poolMinSize = jaxbConn.getPoolMinSize().intValue();
        int poolMaxSize = jaxbConn.getPoolMaxSize().intValue();

        LOG.info( Messages.getMessage( "JDBC_SETTING_UP_CONNECTION_POOL", id, driver, url, user, poolMinSize,
                                       poolMaxSize ) );
        if ( idToPools.containsKey( id ) ) {
            throw new IllegalArgumentException( Messages.getMessage( "JDBC_DUPLICATE_ID", id ) );
        }

        ConnectionPool pool = new ConnectionPool( id, jaxbConn.getDriver(), url, user, password, poolMinSize,
                                                  poolMaxSize );
        idToPools.put( id, pool );
    }

}