package org.example.server;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigurableFilterPipelineTest {

    @Test
    void global_filter_runs() {

        List<String> events = new ArrayList<>();

        HttpFilter filter = new TestFilter(events, false);

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
                List.of("filter", "handler"),
                events
        );
    }

    @Test
    void filter_can_stop_chain() {

        List<String> events = new ArrayList<>();

        HttpFilter stopFilter = new TestFilter(events, true);

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
        assertEquals(List.of("filter"), events);
    }

    // =====================
    // Test helper classes
    // =====================

    static class TestFilter implements HttpFilter {

        private final List<String> events;
        private final boolean stop;

        TestFilter(List<String> events, boolean stop) {
            this.events = events;
            this.stop = stop;
        }

        @Override
        public HttpResponse handle(HttpRequest request, FilterChain chain) {

            events.add("filter");

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
