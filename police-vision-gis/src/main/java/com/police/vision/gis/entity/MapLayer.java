package com.police.vision.gis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gis_map_layer")
public class MapLayer extends BaseEntity {

    private String layerCode;

    private String layerName;

    private String layerType;

    private Integer visible;

    private Integer sortOrder;

    private String style;
}
