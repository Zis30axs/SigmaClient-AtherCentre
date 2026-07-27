package com.elfmcys.yesstevemodel.resource.models;

import com.elfmcys.yesstevemodel.util.data.OrderedStringMap;
import com.elfmcys.yesstevemodel.util.data.StringPair;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Metadata {

    private final String name;

    private final String tips;

    /** first = license type, second = license description. */
    private final StringPair license;

    private final List<AuthorInfo> authors;

    /** e.g. {@code "home" -> "https://modrinth.com/mod/yes-steve-model"}. */
    private final OrderedStringMap<String, String> link;

    public Metadata(String name, String tips, StringPair license, AuthorInfo[] authors, OrderedStringMap<String, String> link) {
        this.name = name;
        this.tips = tips;
        this.license = license;
        this.authors = Collections.unmodifiableList(Arrays.asList(authors));
        this.link = link;
    }

    public String getName() {
        return this.name;
    }

    public String getTips() {
        return this.tips;
    }

    public StringPair getLicense() {
        return this.license;
    }

    public List<AuthorInfo> getAuthors() {
        return this.authors;
    }

    public OrderedStringMap<String, String> getLink() {
        return this.link;
    }
}
