package com.codekaki.odoo.birtrpt;

import javax.json.stream.JsonGenerator;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.birt.report.engine.api.ReportRunner;
import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.server.ResourceConfig;

public class Application extends ResourceConfig {
    static final Logger logger = LogManager.getLogger(ReportRunner.class);

    public Application() {
        packages("com.codekaki.odoo.birtrpt.resource");
        register(LoggingFilter.class);
        property(JsonGenerator.PRETTY_PRINTING, true);
        logger.info("Listing initial context java:comp/env");
        Context ctx;
        try {
            ctx = new InitialContext();
            walk(ctx, "env");
        } catch (NamingException e) {
        }
    }

    private void walk(Context ctx, String name) {
        NamingEnumeration<NameClassPair> list = null;
        try {
            list = ctx.list("java:comp/" + name);
            while (list.hasMore()) {
                NameClassPair pair = list.next();
                if(!pair.getName().equals("__")){
                    String cname = name + "/" + pair.getName();
                    walk(ctx, cname);
                }
            }
        } catch (NamingException e) {
            logger.info("java:comp/" + name);
        }
    }
}
