/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.fdmgateway.utils.jwt;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang.StringEscapeUtils;

import java.util.Map;

@Getter
@Setter
@ToString
public class JwtUserData {

    private String email;
    private String name;
    private String lastName;
    private String winAccountName;
    private String employeeNumber;
    private String sub;

    public JwtUserData(Map<String, String> data) {
        this.email = data.getOrDefault("email", null);
        this.name = data.getOrDefault("given_name", null);
        if(name != null) {
            this.name = StringEscapeUtils.unescapeJava(name.replace("u", "\\u"));
        }
        this.lastName = data.getOrDefault("family_name", null);
        if(lastName != null) {
            this.lastName = StringEscapeUtils.unescapeJava(lastName.replace("u", "\\u"));
        }
        this.winAccountName = data.getOrDefault("winaccountname", null);
        this.employeeNumber = data.getOrDefault("Employee-Number", null);
        this.sub = data.getOrDefault("sub", null);
    }

    public String getFullName() {
        return lastName + " " + name;
    }
}
