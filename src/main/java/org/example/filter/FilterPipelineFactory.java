package org.example.filter;

import org.example.config.AppConfig;
import org.example.config.AppConfig.FilterConfig;

import java.util.ArrayList;
import java.util.List;

public class FilterPipelineFactory {

    public static List<Filter> build(AppConfig config) {

        List<Filter> filters = new ArrayList<>();

        if (config.filters() != null) {
            for (FilterConfig filterConfig : config.filters()) {

                switch (filterConfig.type()) {

                    case "ip":
                        filters.add(new IpFilter(filterConfig.config()));
                        break;

                    case "locale":
                        filters.add(new LocaleFilter());
                        break;

                    default:
                        throw new IllegalArgumentException("Unknown filter: " + filterConfig.type());
                }
            }
        }

        return filters;
    }
}