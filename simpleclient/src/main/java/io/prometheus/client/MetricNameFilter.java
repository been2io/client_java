package io.prometheus.client;

import java.util.*;

import static java.util.Collections.unmodifiableCollection;

public class MetricNameFilter implements Predicate<String> {


    public static final Predicate<String> ACCEPT_ALL = new AcceptAll();

    private final Collection<String> includedNames;
    private final Collection<String> includedPrefixes;
    private final Collection<String> excludedPrefixes;

    @Override
    public boolean test(String metricName) {
        return matchesIncludedNames(metricName)
                && matchesIncludedPrefixes(metricName)
                && !matchesExcludedPrefixes(metricName);
    }

    /**
     * To be replaced with Java 8's {@code Predicate.and()} once we drop support for older Java versions.
     */
    public Predicate<String> and(final Predicate<? super String> other) {
        return new Predicate<String>() {
            @Override
            public boolean test(String s) {
                return MetricNameFilter.this.test(s) && other.test(s);
            }
        };
    }

    private boolean matchesIncludedNames(String metricName) {
        if (includedNames.isEmpty()) {
            return true;
        }
        return includedNames.contains(metricName);
    }

    private boolean matchesIncludedPrefixes(String metricName) {
        if (includedPrefixes.isEmpty()) {
            return true;
        }
        for (String prefix : includedPrefixes) {
            if (metricName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesExcludedPrefixes(String metricName) {
        if (excludedPrefixes.isEmpty()) {
            return false;
        }
        for (String prefix : excludedPrefixes) {
            if (metricName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static class Builder {

        private final Collection<String> includedNames = new ArrayList<String>();
        private final Collection<String> includedPrefixes = new ArrayList<String>();
        private final Collection<String> excludedPrefixes = new ArrayList<String>();

        public Builder() {}

        private Builder(MetricNameFilter template) {
            includeNames(template.includedNames);
            includePrefixes(template.includedPrefixes);
            excludePrefixes(template.excludedPrefixes);
        }

        public Builder includeNames(String... names) {
            return includeNames(Arrays.asList(names));
        }

        public Builder includeNames(Collection<String> names) {
            includedNames.addAll(names);
            return this;
        }

        public Builder includePrefixes(String... prefixes) {
            return includePrefixes(Arrays.asList(prefixes));
        }

        public Builder includePrefixes(Collection<String> prefixes) {
            includedPrefixes.addAll(prefixes);
            return this;
        }

        public Builder excludePrefixes(String... prefixes) {
            return excludePrefixes(Arrays.asList(prefixes));
        }

        public Builder excludePrefixes(Collection<String> prefixes) {
            excludedPrefixes.addAll(prefixes);
            return this;
        }

        public MetricNameFilter build() {
            return new MetricNameFilter(includedNames, includedPrefixes, excludedPrefixes);
        }
    }

    private MetricNameFilter(Collection<String> includedNames, Collection<String> includedPrefixes, Collection<String> excludedPrefixes) {
        this.includedNames = unmodifiableCollection(includedNames);
        this.includedPrefixes = unmodifiableCollection(includedPrefixes);
        this.excludedPrefixes = unmodifiableCollection(excludedPrefixes);
    }

    private static class AcceptAll implements Predicate<String> {

        private AcceptAll() {
        }

        @Override
        public boolean test(String s) {
            return true;
        }
    }
}
