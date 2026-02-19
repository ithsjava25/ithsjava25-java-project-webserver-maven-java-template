package org.example.server;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigurableFilterPipelineTest {

    @Test
    void global_filter_runs() {

        List<String> events = new ArrayList<>();

        HttpFilter filter = new TestFilter("g1", events, false);

        List<FilterRegistration> regs = List.of(
                new FilterRegistration(filter, 1, null)
        );

        ConfigurableFilterPipeline pipeline =
                new ConfigurableFilterPipeline(regs);

        pipeline.execute(
                new HttpRequest("GET", "/home"),
                new TestHandler(events)
        );

        assertEquals(
                List.of("g1", "handler"),
                events
        );
    }

    @Test
    void filter_can_stop_chain() {

        List<String> events = new ArrayList<>();

        HttpFilter stopFilter = new TestFilter("stop", events, true);

        List<FilterRegistration> regs = List.of(
                new FilterRegistration(stopFilter, 1, null)
        );

        ConfigurableFilterPipeline pipeline =
                new ConfigurableFilterPipeline(regs);

        HttpResponse response = pipeline.execute(
                new HttpRequest("GET", "/home"),
                new TestHandler(events)
        );

        assertEquals(403, response.getStatusCode());
        assertEquals(List.of("stop"), events);
    }

    @Test
    void route_specific_filter_runs_when_path_matches() {
        List<String> events = new ArrayList<>();

        HttpFilter routeFilter = new TestFilter("r1", events, false);

        List<FilterRegistration> regs = List.of(
                new FilterRegistration(routeFilter, 1, List.of("/api/*"))
        );

        ConfigurableFilterPipeline pipeline =
                new ConfigurableFilterPipeline(regs);

        pipeline.execute(
                new HttpRequest("GET", "/api/users"),
                new TestHandler(events)
        );

        assertEquals(List.of("r1", "handler"), events);
    }

    @Test
    void route_specific_filter_is_skipped_when_path_does_not_match() {
        List<String> events = new ArrayList<>();

        HttpFilter routeFilter = new TestFilter("r1", events, false);

        List<FilterRegistration> regs = List.of(
                new FilterRegistration(routeFilter, 1, List.of("/api/*"))
        );

        ConfigurableFilterPipeline pipeline =
                new ConfigurableFilterPipeline(regs);

        pipeline.execute(
                new HttpRequest("GET", "/public"),
                new TestHandler(events)
        );

        assertEquals(List.of("handler"), events);
    }

    @Test
    void mixed_pipeline_runs_global_then_route_then_handler() {
        List<String> events = new ArrayList<>();

        HttpFilter global = new TestFilter("g1", events, false);
        HttpFilter route = new TestFilter("r1", events, false);

        List<FilterRegistration> regs = List.of(
                new FilterRegistration(global, 1, null),
                new FilterRegistration(route, 1, List.of("/api/*"))
        );

        ConfigurableFilterPipeline pipeline =
                new ConfigurableFilterPipeline(regs);

        pipeline.execute(
                new HttpRequest("GET", "/api/users"),
                new TestHandler(events)
        );

        assertEquals(List.of("g1", "r1", "handler"), events);
    }

    @Test
    void ordering_is_by_order_field() {
        List<String> events = new ArrayList<>();

        HttpFilter f20 = new TestFilter("f20", events, false);
        HttpFilter f10 = new TestFilter("f10", events, false);
        HttpFilter f30 = new TestFilter("f30", events, false);

        List<FilterRegistration> regs = List.of(
                new FilterRegistration(f20, 20, null),
                new FilterRegistration(f10, 10, null),
                new FilterRegistration(f30, 30, null)
        );

        ConfigurableFilterPipeline pipeline =
                new ConfigurableFilterPipeline(regs);

        pipeline.execute(
                new HttpRequest("GET", "/home"),
                new TestHandler(events)
        );

        assertEquals(List.of("f10", "f20", "f30", "handler"), events);
    }

    @Test
    void global_stop_filter_prevents_route_and_handler() {
        List<String> events = new ArrayList<>();

        HttpFilter globalStop = new TestFilter("gStop", events, true);
        HttpFilter routeFilter = new TestFilter("r1", events, false);

        List<FilterRegistration> regs = List.of(
                new FilterRegistration(globalStop, 1, null),
                new FilterRegistration(routeFilter, 1, List.of("/api/*"))
        );

        ConfigurableFilterPipeline pipeline =
                new ConfigurableFilterPipeline(regs);

        HttpResponse response = pipeline.execute(
                new HttpRequest("GET", "/api/users"),
                new TestHandler(events)
        );

        assertEquals(403, response.getStatusCode());
        assertEquals(List.of("gStop"), events);
    }

    @Test
    void route_stop_filter_prevents_handler_but_global_runs() {
        List<String> events = new ArrayList<>();

        HttpFilter global = new TestFilter("g1", events, false);
        HttpFilter routeStop = new TestFilter("rStop", events, true);

        List<FilterRegistration> regs = List.of(
                new FilterRegistration(global, 1, null),
                new FilterRegistration(routeStop, 1, List.of("/api/*"))
        );

        ConfigurableFilterPipeline pipeline =
                new ConfigurableFilterPipeline(regs);

        HttpResponse response = pipeline.execute(
                new HttpRequest("GET", "/api/users"),
                new TestHandler(events)
        );

        assertEquals(403, response.getStatusCode());
        assertEquals(List.of("g1", "rStop"), events);
    }


    static class TestFilter implements HttpFilter {

        private final String name;
        private final List<String> events;
        private final boolean stop;

        TestFilter(String name, List<String> events, boolean stop) {
            this.name = name;
            this.events = events;
            this.stop = stop;
        }

        @Override
        public HttpResponse handle(HttpRequest request, FilterChain chain) {

            events.add(name);

            if (stop) {
                return new HttpResponse(403, "Stopped");
            }

            return chain.next(request);
        }
    }

    static class TestHandler implements TerminalHandler {

        private final List<String> events;

        TestHandler(List<String> events) {
            this.events = events;
        }

        @Override
        public HttpResponse handle(HttpRequest request) {
            events.add("handler");
            return new HttpResponse(200, "OK");
        }
    }
}
