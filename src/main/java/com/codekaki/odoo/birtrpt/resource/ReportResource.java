package com.codekaki.odoo.birtrpt.resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.StreamingOutput;

import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.core.framework.Platform;
import org.eclipse.birt.report.engine.api.EngineConfig;
import org.eclipse.birt.report.engine.api.EngineException;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportEngineFactory;
import org.eclipse.birt.report.engine.api.IReportRunnable;
import org.eclipse.birt.report.engine.api.ReportRunner;
import org.eclipse.core.internal.registry.RegistryProviderFactory;

@Path("/report")
public class ReportResource {
    static protected Logger logger = Logger.getLogger(ReportRunner.class.getName());

    static final JsonObject EMPTY_JSON_OBJECT = Json.createObjectBuilder().build();
    static final JsonArray EMPTY_JSON_ARRAY = Json.createArrayBuilder().build();
    static IReportEngine reportEngine;

    static IReportEngine getReportEngine() {
        if (reportEngine == null) {
            EngineConfig config = new EngineConfig();
            try {
                Context ctx = new InitialContext();
                String resourcePath = (String) ctx.lookup("java:comp/env/birt/resources");
                config.setResourcePath(resourcePath);
                logger.log(Level.INFO, "Resource path: " + config.getResourcePath());
            } catch (NamingException e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
            }

            try {
                Platform.startup(config);
            } catch (BirtException e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
            }

            IReportEngineFactory reportEngineFactory = (IReportEngineFactory) Platform
                    .createFactoryObject(IReportEngineFactory.EXTENSION_REPORT_ENGINE_FACTORY);
            reportEngine = reportEngineFactory.createReportEngine(config);
        }
        return reportEngine;
    }
    

    void shutdown(){
        reportEngine.destroy();
        Platform.shutdown();
        RegistryProviderFactory.releaseDefault();  // bugzilla 351052
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonArray parameters(@QueryParam("report_file") String report_file) {
        if (report_file == null) {
            return EMPTY_JSON_ARRAY;
        }

        ReportParametersInspector inspector = new ReportParametersInspector(getReportEngine());
        IReportRunnable reportRunnable = getReportDesign(report_file);
        return inspector.enumParameters(reportRunnable);
    }
    
    
    @POST
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(final JsonObject args) {
        String report_file = args.getString("report_file", null);
        String format = args.getString("format", "PDF");
        String htmlType = args.getString("htmlType", null);
        String encoding = args.getString("encoding", "UTF-8");
        String locale = args.getString("locale", "en");
        JsonObject values = args.getJsonObject("values");
        
        IReportRunnable reportRunnable = getReportDesign(report_file);
        ReportGenerator generator = new ReportGenerator(getReportEngine(), encoding, locale);

        try {
            String path = buildTargetFilePath(UUID.randomUUID().toString(), format).getPath();
            generator.run(reportRunnable, format, htmlType, path, values);
            return buildFileResponseOk(report_file, path, format);
        } catch (NamingException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        } catch (EngineException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return Response.status(Response.Status.BAD_REQUEST).build();
    }
    
    IReportRunnable getReportDesign(String report_file) {
        java.nio.file.Path path = null;
        try {
            Context ctx = new InitialContext();
            String reportsDirectory = (String) ctx.lookup("java:comp/env/birt/reports");
            path = Paths.get(reportsDirectory, report_file).normalize();
            if (path != null) {
                return getReportEngine().openReportDesign(path.toFile().getPath());
            }
        } catch (NamingException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        } catch (EngineException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }

        return null;
    }

    File buildTargetFilePath(String targetFile, String format) throws NamingException {
        InitialContext ctx = new InitialContext();
        String targetDir = (String) ctx.lookup("java:comp/env/birt/output");
        targetFile += "." + format.toLowerCase();
        File file = Paths.get(targetDir, targetFile).normalize().toFile();
        return file;
    }

    Response buildFileResponseOk(String report_file, final String path, final String format) {
        StreamingOutput stream = new StreamingOutput() {
            @Override
            public void write(OutputStream output) throws IOException {
                FileInputStream fis = null;
                try {
                    byte[] buf = new byte[4096];
                    int len = 0;
                    fis = new FileInputStream(path);
                    do {
                        len = fis.read(buf);
                        if (len != -1) {
                            output.write(buf, 0, len);
                        }
                    } while (len != -1);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, e.getMessage(), e);
                } finally {
                    try {
                        Files.deleteIfExists(Paths.get(path));
                        if (fis != null) {
                            fis.close();
                        }
                        output.close();
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, e.getMessage(), e);
                    }
                }
            }
        };

        String fileName = new File(path).getName();
        ResponseBuilder response = Response.ok(stream, getReportEngine().getMIMEType(format)).header(
                "content-disposition", "attachment; filename = " + fileName);

        return response.build();
    }
}
