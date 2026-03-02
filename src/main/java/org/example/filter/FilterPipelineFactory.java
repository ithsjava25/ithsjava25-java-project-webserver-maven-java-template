package org.example.filter;

import java.util.ArrayList;
import java.util.List;

public class FilterPipelineFactory {

    public static List<Filter> buildFilters(List<String> filterNames) {
        List<Filter> filters = new ArrayList<>();
        for (String name : filterNames) {
            switch (name) {
                case "LocaleFilter":
                    filters.add(new LocaleFilter());
                    break;
                default:
                    throw new IllegalArgumentException("Unknown filter " + name);
            }
        }
        return filters;
    }
}