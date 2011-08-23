//$HeadURL$
/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2010 by:
 - Department of Geography, University of Bonn -
 and
 - lat/lon GmbH -

 This library is free software; you can redistribute it and/or modify it under
 the terms of the GNU Lesser General Public License as published by the Free
 Software Foundation; either version 2.1 of the License, or (at your option)
 any later version.
 This library is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 details.
 You should have received a copy of the GNU Lesser General Public License
 along with this library; if not, write to the Free Software Foundation, Inc.,
 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

 Contact information:

 lat/lon GmbH
 Aennchenstr. 19, 53177 Bonn
 Germany
 http://lat-lon.de/

 Department of Geography, University of Bonn
 Prof. Dr. Klaus Greve
 Postfach 1147, 53001 Bonn
 Germany
 http://www.geographie.uni-bonn.de/deegree/

 e-mail: info@deegree.org
 ----------------------------------------------------------------------------*/
package org.deegree.coverage.persistence.pyramid;

import static org.deegree.commons.xml.jaxb.JAXBUtils.unmarshall;
import static org.deegree.coverage.raster.io.RasterIOOptions.CRS;
import static org.deegree.coverage.raster.io.RasterIOOptions.IMAGE_INDEX;
import static org.deegree.coverage.raster.io.RasterIOOptions.OPT_FORMAT;
import it.geosolutions.imageio.plugins.geotiff.GeoTiffImageReader;

import java.io.File;
import java.net.URL;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;

import org.deegree.commons.config.DeegreeWorkspace;
import org.deegree.commons.config.ResourceInitException;
import org.deegree.commons.config.ResourceManager;
import org.deegree.coverage.Coverage;
import org.deegree.coverage.persistence.CoverageBuilder;
import org.deegree.coverage.persistence.pyramid.jaxb.Pyramid;
import org.deegree.coverage.raster.AbstractRaster;
import org.deegree.coverage.raster.MultiResolutionRaster;
import org.deegree.coverage.raster.io.RasterIOOptions;
import org.deegree.coverage.raster.io.imageio.geotiff.GeoTiffIIOMetadataAdapter;
import org.deegree.coverage.raster.utils.RasterFactory;
import org.deegree.cs.configuration.wkt.WKTParser;
import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.cs.exceptions.UnknownCRSException;
import org.deegree.cs.persistence.CRSManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author <a href="mailto:schmitz@lat-lon.de">Andreas Schmitz</a>
 * @author last edited by: $Author$
 * 
 * @version $Revision$, $Date$
 */
public class PyramidProvider implements CoverageBuilder {

    private static Logger LOG = LoggerFactory.getLogger( PyramidProvider.class );

    private static final URL CONFIG_SCHEMA = PyramidProvider.class.getResource( "/META-INF/schemas/datasource/coverage/raster/3.1.0/pyramid.xsd" );

    private DeegreeWorkspace workspace;

    @Override
    public void init( DeegreeWorkspace workspace ) {
        this.workspace = workspace;
    }

    private static ICRS getCRS( IIOMetadata metaData ) {
        GeoTiffIIOMetadataAdapter geoTIFFMetaData = new GeoTiffIIOMetadataAdapter( metaData );
        try {
            int modelType = Integer.valueOf( geoTIFFMetaData.getGeoKey( GeoTiffIIOMetadataAdapter.GTModelTypeGeoKey ) );
            String epsgCode = null;
            if ( modelType == GeoTiffIIOMetadataAdapter.ModelTypeProjected ) {
                epsgCode = geoTIFFMetaData.getGeoKey( GeoTiffIIOMetadataAdapter.ProjectedCSTypeGeoKey );
            } else if ( modelType == GeoTiffIIOMetadataAdapter.ModelTypeGeographic ) {
                epsgCode = geoTIFFMetaData.getGeoKey( GeoTiffIIOMetadataAdapter.GeographicTypeGeoKey );
            }
            if ( epsgCode != null && epsgCode.length() != 0 ) {
                try {
                    return CRSManager.lookup( "EPSG:" + epsgCode );
                } catch ( UnknownCRSException e ) {
                    LOG.error( "No coordinate system found for EPSG:" + epsgCode );
                }
            }
        } catch ( UnsupportedOperationException ex ) {
            LOG.debug( "couldn't read crs information in GeoTIFF" );
        }
        return null;
    }

    @Override
    public Coverage create( URL configUrl )
                            throws ResourceInitException {
        try {
            Pyramid config = (Pyramid) unmarshall( "org.deegree.coverage.persistence.pyramid.jaxb", CONFIG_SCHEMA,
                                                   configUrl, workspace );
            Iterator<ImageReader> readers = ImageIO.getImageReadersBySuffix( "tif" );
            ImageReader reader = readers.next();
            while ( readers.hasNext() && !reader.getClass().getSimpleName().equals( "GeoTiffImageReader" ) ) {
                reader = readers.next();
            }

            if ( !reader.getClass().getSimpleName().equals( "GeoTiffImageReader" ) ) {
                LOG.warn( "GDAL GeoTiff reader not available, will be unable to read BigTiff." );
            }

            MultiResolutionRaster mrr = new MultiResolutionRaster();
            String file = config.getPyramidFile();

            ImageInputStream iis = ImageIO.createImageInputStream( new File( file ) );
            reader.setInput( iis );

            IIOMetadata md = reader.getStreamMetadata();
            ICRS crs = getCRS( md );

            if ( reader instanceof GeoTiffImageReader ) {
                GeoTiffImageReader gt = (GeoTiffImageReader) reader;
                String proj = gt.getProjection( 0 );
                crs = WKTParser.parse( proj );
            }

            int num = reader.getNumImages( true );
            iis.close();
            for ( int i = 0; i < num; ++i ) {
                RasterIOOptions opts = new RasterIOOptions();
                opts.add( IMAGE_INDEX, "" + i );
                opts.add( OPT_FORMAT, "tif" );
                if ( crs != null ) {
                    opts.add( CRS, crs.getAlias() );
                } else {
                    opts.add( CRS, "EPSG:25832" );
                }
                AbstractRaster raster = RasterFactory.loadRasterFromFile( new File( file ), opts );
                mrr.addRaster( raster );
            }
            mrr.setCoordinateSystem( crs );
            return mrr;
        } catch ( Throwable e ) {
            throw new ResourceInitException( "Could not read pyramid configuration file.", e );
        }
    }

    @Override
    public Class<? extends ResourceManager>[] getDependencies() {
        return new Class[] {};
    }

    @Override
    public String getConfigNamespace() {
        return "http://www.deegree.org/datasource/coverage/pyramid";
    }

    @Override
    public URL getConfigSchema() {
        return CONFIG_SCHEMA;
    }

}
