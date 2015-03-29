package com.codekaki.odoo.birtrpt;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.stream.JsonGenerator;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.eclipse.birt.report.engine.api.ReportRunner;
import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.server.ResourceConfig;

public class Application extends ResourceConfig {
    static protected Logger logger = Logger.getLogger(ReportRunner.class.getName());
    
    public Application() {
        packages("com.codekaki.odoo.birtrpt.resource");
        register(LoggingFilter.class);
        property(JsonGenerator.PRETTY_PRINTING, true);
        try {
            logger.log(Level.INFO, "Listing initial context java:comp/env");
            Context ctx = new InitialContext();
            NamingEnumeration<NameClassPair> list = ctx.list("java:comp/env");
            while(list.hasMore()){
                NameClassPair pair = list.next();
                logger.log(Level.INFO, "- " + pair.getName());
            }
            System.out.println("No more entries!");
        } catch (NamingException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }
}
