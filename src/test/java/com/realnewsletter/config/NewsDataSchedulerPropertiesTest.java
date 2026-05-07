package com.realnewsletter.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NewsDataSchedulerProperties} to verify default values,
 * getters and setters all work correctly.
 */
class NewsDataSchedulerPropertiesTest {

    @Test
    void defaultValues_shouldMatchExpected() {
        NewsDataSchedulerProperties props = new NewsDataSchedulerProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getCron()).isEqualTo("0 0 6 * * *");
        assertThat(props.getMaxRequests()).isEqualTo(100);
        assertThat(props.getPageSize()).isEqualTo(10);
        assertThat(props.getCountry()).isEqualTo("us");
        assertThat(props.getLanguage()).isEqualTo("en");
        // category defaults to null so it is omitted from API requests (fetches all categories)
        assertThat(props.getCategory()).isNull();
    }

    @Test
    void setters_shouldUpdateAllFields() {
        NewsDataSchedulerProperties props = new NewsDataSchedulerProperties();
        props.setEnabled(false);
        props.setCron("0 0 12 * * *");
        props.setMaxRequests(50);
        props.setPageSize(5);
        props.setCountry("in");
        props.setLanguage("hi");
        props.setCategory("technology");

        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getCron()).isEqualTo("0 0 12 * * *");
        assertThat(props.getMaxRequests()).isEqualTo(50);
        assertThat(props.getPageSize()).isEqualTo(5);
        assertThat(props.getCountry()).isEqualTo("in");
        assertThat(props.getLanguage()).isEqualTo("hi");
        assertThat(props.getCategory()).isEqualTo("technology");
    }
}

