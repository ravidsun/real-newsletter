package com.realnewsletter.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewsApiSchedulerPropertiesTest {

    @Test
    void defaultValues_shouldMatchExpected() {
        NewsApiSchedulerProperties props = new NewsApiSchedulerProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getCron()).isEqualTo("0 0 7 * * *");
        assertThat(props.getMaxRequests()).isEqualTo(50);
        assertThat(props.getPageSize()).isEqualTo(20);
        assertThat(props.getCountry()).isEqualTo("us");
        assertThat(props.getLanguage()).isEqualTo("en");
        assertThat(props.getCategory()).isEqualTo("general");
    }

    @Test
    void setters_shouldUpdateAllFields() {
        NewsApiSchedulerProperties props = new NewsApiSchedulerProperties();
        props.setEnabled(false);
        props.setCron("0 0 8 * * *");
        props.setMaxRequests(10);
        props.setPageSize(5);
        props.setCountry("in");
        props.setLanguage("hi");
        props.setCategory("technology");

        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getCron()).isEqualTo("0 0 8 * * *");
        assertThat(props.getMaxRequests()).isEqualTo(10);
        assertThat(props.getPageSize()).isEqualTo(5);
        assertThat(props.getCountry()).isEqualTo("in");
        assertThat(props.getLanguage()).isEqualTo("hi");
        assertThat(props.getCategory()).isEqualTo("technology");
    }
}

