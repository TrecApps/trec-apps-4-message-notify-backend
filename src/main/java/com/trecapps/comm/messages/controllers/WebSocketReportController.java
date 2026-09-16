package com.trecapps.comm.messages.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.handler.HandlerMethod;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class WebSocketReportController {

    @Autowired(required = false)
    private SimpAnnotationMethodMessageHandler simpHandler;
    @Autowired
    ApplicationContext applicationContext;

    @GetMapping("/api/websocket-endpoints")
    public Map<String, Object> getWebSocketMappings() {
        Map<String, Object> result = new HashMap<>();
        Map<String, HandlerMapping> handlerMappings = applicationContext.getBeansOfType(HandlerMapping.class);

        for (Map.Entry<String, HandlerMapping> entry : handlerMappings.entrySet()) {
            HandlerMapping mapping = entry.getValue();
            if (mapping instanceof SimpleUrlHandlerMapping) {
                SimpleUrlHandlerMapping urlMapping = (SimpleUrlHandlerMapping) mapping;
                Map<String, Object> urlMap = urlMapping.getHandlerMap();

                // Filter or collect paths that relate to websocket/stomp handshakes
                urlMap.forEach((path, handler) -> {
                    //if (handler.toString().contains("WebSocket") || handler.toString().contains("SockJS")) {
                        result.put(path, handler.getClass().getSimpleName());
                    //}
                });
            }
        }
        return result;
    }
}

