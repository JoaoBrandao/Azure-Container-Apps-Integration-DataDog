package com.jb.demo;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/demo/{user}")
public class DemoResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String demoResource(@PathParam("user") final String user) {
        return "hello " + user;
    }
}