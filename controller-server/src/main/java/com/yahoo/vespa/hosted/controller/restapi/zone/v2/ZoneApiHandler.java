// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.zone.v2;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.proxy.ConfigServerRestExecutor;
import com.yahoo.vespa.hosted.controller.proxy.ProxyException;
import com.yahoo.vespa.hosted.controller.proxy.ProxyRequest;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.Path;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * REST API for proxying requests to config servers in a given zone (version 2).
 *
 * This API does something completely different from /zone/v1, but such is the world.
 *
 * @author mpolden
 */
@SuppressWarnings("unused")
public class ZoneApiHandler extends LoggingRequestHandler {

    private final ZoneRegistry zoneRegistry;
    private final ConfigServerRestExecutor proxy;

    public ZoneApiHandler(Executor executor, AccessLog accessLog, ZoneRegistry zoneRegistry,
                          ConfigServerRestExecutor proxy) {
        super(executor, accessLog);
        this.zoneRegistry = zoneRegistry;
        this.proxy = proxy;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET:
                    return get(request);
                case POST:
                case PUT:
                case DELETE:
                case PATCH:
                    return proxy(request);
                default:
                    return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is unsupported");
            }
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse get(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (path.matches("/zone/v2")) {
            return root(request);
        }
        return proxy(request);
    }

    private HttpResponse proxy(HttpRequest request) {
        Path path = new Path(request.getUri().getPath());
        if (!path.matches("/zone/v2/{environment}/{region}/{*}")) {
            return notFound(path);
        }
        Environment environment = Environment.from(path.get("environment"));
        RegionName region = RegionName.from(path.get("region"));
        Optional<Zone> zone = zoneRegistry.getZone(environment, region);
        if (!zone.isPresent()) {
            throw new IllegalArgumentException("No such zone: " + environment.value() + "." + region.value());
        }
        try {
            return proxy.handle(new ProxyRequest(request, "/zone/v2/"));
        } catch (ProxyException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpResponse root(HttpRequest request) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor uris = root.setArray("uris");
        zoneRegistry.zones().forEach(zone -> uris.addString(request.getUri()
                                                               .resolve("/zone/v2/")
                                                               .resolve(zone.environment().value() + "/")
                                                               .resolve(zone.region().value())
                                                               .toString()));
        Cursor zones = root.setArray("zones");
        zoneRegistry.zones().forEach(zone -> {
            Cursor object = zones.addObject();
            object.setString("environment", zone.environment().value());
            object.setString("region", zone.region().value());
        });
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse notFound(Path path) {
        return ErrorResponse.notFoundError("Nothing at " + path);
    }
}
