package com.elfmcys.yesstevemodel.resource.models;

import com.elfmcys.yesstevemodel.util.data.OrderedStringMap;

public class AuthorInfo {

    private final String name;

    private final String role;

    private final OrderedStringMap<String, String> contact;

    private final String comment;

    public AuthorInfo(String name, String role, OrderedStringMap<String, String> contact, String comment) {
        this.name = name;
        this.role = role;
        this.contact = contact;
        this.comment = comment;
    }

    public String getName() {
        return this.name;
    }

    public String getRole() {
        return this.role;
    }

    public OrderedStringMap<String, String> getContact() {
        return this.contact;
    }

    public String getComment() {
        return this.comment;
    }
}
