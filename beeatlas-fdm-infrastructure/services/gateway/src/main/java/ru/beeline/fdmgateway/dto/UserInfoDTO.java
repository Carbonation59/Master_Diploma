/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.fdmgateway.dto;

import lombok.*;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class UserInfoDTO {

    private Integer id;
    private List<Long> productsIds;

    private List<String> roles;

    private List<PermissionTypeDTO> permissions;

}
