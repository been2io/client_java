package io.prometheus.client;

import java.util.*;

import static java.util.Collections.unmodifiableCollection;

public class SampleNameFilter implements Predicate<String> {

    /**
     * For convenience, a filter that allows all time series names.
     */
    public static final Predicate<String> ALLOW_ALL = new AcceptAll();

    private final Collection<String> allowedNames;
    private final Collection<String> excludedNames;
    private final Collection<String> allowedPrefixes;
    private final Collection<String> excludedPrefixes;

    @Override
    public boolean test(String timeSeriesName) {
        return matchesAllowedNames(timeSeriesName)
                && !matchesExcludedNames(timeSeriesName)
                && matchesAllowedPrefixes(timeSeriesName)
                && !matchesExcludedPrefixes(timeSeriesName);
    }

    /**
     * To be replaced with Java 8's {@code Predicate.and()} once we drop support for older Java versions.
     */
    public Predicate<String> and(final Predicate<? super String> other) {
        if (other == null) {
            throw new NullPointerException();
        }
        return new Predicate<String>() {
            @Override
            public boolean test(String s) {
                return SampleNameFilter.this.test(s) && other.test(s);
            }
        };
    }

    private boolean matchesAllowedNames(String metricName) {
        if (allowedNames.isEmpty()) {
            return true;
        }
        return allowedNames.contains(metricName);
    }

    private boolean matchesExcludedNames(String metricName) {
        if (excludedNames.isEmpty()) {
            return false;
        }
        return excludedNames.contains(metricName);
    }

    private boolean matchesAllowedPrefixes(String metricName) {
        if (allowedPrefixes.isEmpty()) {
            return true;
        }
        for (String prefix : allowedPrefixes) {
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

    public static class Builder {

        private final Collection<String> includedNames = new ArrayList<String>();
        private final Collection<String> excludedNames = new ArrayList<String>();
        private final Collection<String> includedPrefixes = new ArrayList<String>();
        private final Collection<String> excludedPrefixes = new ArrayList<String>();

        /**
         * @see #nameMustBeEqualTo(Collection)
         */
        public Builder nameMustBeEqualTo(String... names) {
            return nameMustBeEqualTo(Arrays.asList(names));
        }

        /**
         * Only time series with one of the {@code names} will be included.
         * <p>
         * Note that the provided {@code names} will be matched against the time series name and not the metric name.
         * For instance, to retrieve all samples from a histogram, you must include the '_count', '_sum' and '_bucket'
         * names.
         * <p>
         * This method should be used by HTTP exporters to implement the {@code ?name[]=} URL parameters.
         *
         * @param names empty means no restriction.
         */
        public Builder nameMustBeEqualTo(Collection<String> names) {
            includedNames.addAll(names);
            return this;
        }

        /**
         * @see #nameMustNotBeEqualTo(Collection)
         */
        public Builder nameMustNotBeEqualTo(String... names) {
            return nameMustNotBeEqualTo(Arrays.asList(names));
        }

        /**
         * All time series that are not in {@code names} will be excluded.
         * <p>
         * Note that the provided {@code names} will be matched against the time series name and not the metric name.
         * For instance, to exclude all samples from a histogram, you must exclude the '_count', '_sum' and '_bucket'
         * names.
         *
         * @param names empty means no name will be excluded.
         */
        public Builder nameMustNotBeEqualTo(Collection<String> names) {
            excludedNames.addAll(names);
            return this;
        }

        /**
         * @see #nameMustStartWith(Collection)
         */
        public Builder nameMustStartWith(String... prefixes) {
            return nameMustStartWith(Arrays.asList(prefixes));
        }

        /**
         * Only time series with names starting with one of the {@code prefixes} will be included.
         *
         * @param prefixes empty means no restriction.
         */
        public Builder nameMustStartWith(Collection<String> prefixes) {
            includedPrefixes.addAll(prefixes);
            return this;
        }

        /**
         * @see #nameMustNotStartWith(Collection)
         */
        public Builder nameMustNotStartWith(String... prefixes) {
            return nameMustNotStartWith(Arrays.asList(prefixes));
        }

        /**
         * Time series with names starting with one of the {@code prefixes} will be excluded.
         * @param prefixes empty means no time series will be excluded.
         */
        public Builder nameMustNotStartWith(Collection<String> prefixes) {
            excludedPrefixes.addAll(prefixes);
            return this;
        }

        public SampleNameFilter build() {
            return new SampleNameFilter(includedNames, excludedNames, includedPrefixes, excludedPrefixes);
        }
    }

    private SampleNameFilter(Collection<String> allowedNames, Collection<String> excludedNames, Collection<String> allowedPrefixes, Collection<String> excludedPrefixes) {
        this.allowedNames = unmodifiableCollection(allowedNames);
        this.excludedNames = unmodifiableCollection(excludedNames);
        this.allowedPrefixes = unmodifiableCollection(allowedPrefixes);
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

    /**
     * Helper method to deserialize a {@code delimiter}-separated list of Strings into a {@code List<String>}.
     * <p>
     * {@code delimiter} is one of {@code , ; \t \n}.
     * <p>
     * This is implemented here so that exporters can provide a consistent configuration format for
     * lists of allowed names.
     */
    public static List<String> stringToList(String s) {
        List<String> result = new ArrayList<String>();
        if (s != null) {
            StringTokenizer tokenizer = new StringTokenizer(s, ",; \t\n");
            while (tokenizer.hasMoreTokens()) {
                String token = tokenizer.nextToken();
                token = token.trim();
                if (token.length() > 0) {
                    result.add(token);
                }
            }
        }
        return result;
    }

    /**
     * Helper method to compose a filter such that Sample names must
     * <ul>
     *     <li>match the existing filter</li>
     *     <li>and be in the list of allowedNames</li>
     * </ul>
     * This should be used to implement the {@code names[]} query parameter in HTTP exporters.
     *
     * @param filter may be null, indicating that the resulting filter should just filter by {@code allowedNames}.
     * @param allowedNames may be null or empty, indicating that {@code filter} is returned unmodified.
     * @return a filter combining the exising {@code filter} and the {@code allowedNames}, or {@code null}
     *         if both parameters were {@code null}.
     */
    public static Predicate<String> restrictToNamesEqualTo(Predicate<String> filter, Collection<String> allowedNames) {
        if (allowedNames != null && !allowedNames.isEmpty()) {
            SampleNameFilter includedNamesFilter = new SampleNameFilter.Builder()
                    .nameMustBeEqualTo(allowedNames)
                    .build();
            if (filter == null) {
                return includedNamesFilter;
            } else {
                return includedNamesFilter.and(filter);
            }
        }
        return filter;
    }
}
