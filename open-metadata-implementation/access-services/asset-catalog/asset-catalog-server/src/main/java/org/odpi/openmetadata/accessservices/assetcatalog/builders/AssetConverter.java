/* SPDX-License-Identifier: Apache 2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.openmetadata.accessservices.assetcatalog.builders;

import org.apache.commons.collections4.CollectionUtils;
import org.odpi.openmetadata.accessservices.assetcatalog.model.AssetDescription;
import org.odpi.openmetadata.accessservices.assetcatalog.model.AssetElement;
import org.odpi.openmetadata.accessservices.assetcatalog.model.AssetElements;
import org.odpi.openmetadata.accessservices.assetcatalog.model.Classification;
import org.odpi.openmetadata.accessservices.assetcatalog.model.Element;
import org.odpi.openmetadata.accessservices.assetcatalog.model.Relationship;
import org.odpi.openmetadata.accessservices.assetcatalog.model.Type;
import org.odpi.openmetadata.accessservices.assetcatalog.util.Constants;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.EntityDetail;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.EntityProxy;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceType;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDef;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AssetConverter is a helper class that maps the OMRS objects to Asset Catalog model.
 */
public class AssetConverter {

    private OMRSRepositoryHelper repositoryHelper;

    public AssetConverter(OMRSRepositoryHelper repositoryHelper) {
        this.repositoryHelper = repositoryHelper;
    }

    public AssetDescription getAssetDescription(EntityDetail entityDetail) {
        AssetDescription assetDescription = new AssetDescription();
        assetDescription.setGuid(entityDetail.getGUID());
        assetDescription.setMetadataCollectionId(entityDetail.getMetadataCollectionId());

        assetDescription.setCreatedBy(entityDetail.getCreatedBy());
        assetDescription.setCreateTime(entityDetail.getCreateTime());
        assetDescription.setUpdatedBy(entityDetail.getUpdatedBy());
        assetDescription.setUpdateTime(entityDetail.getUpdateTime());
        assetDescription.setVersion(entityDetail.getVersion());

        if (entityDetail.getType() != null && entityDetail.getType().getTypeDefName() != null) {
            assetDescription.setType(convertInstanceType(entityDetail.getType()));
        }

        assetDescription.setUrl(entityDetail.getInstanceURL());
        if (entityDetail.getStatus() != null && entityDetail.getStatus().getName() != null) {
            assetDescription.setStatus(entityDetail.getStatus().getName());
        }

        if (entityDetail.getProperties() != null) {
            assetDescription.setProperties(repositoryHelper.getInstancePropertiesAsMap(entityDetail.getProperties()));
        }
        if (entityDetail.getClassifications() != null) {
            assetDescription.setClassifications(convertClassifications(entityDetail.getClassifications()));
        }

        return assetDescription;
    }

    public List<Relationship> convertRelationships(List<org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.Relationship> relationships) {
        if (relationships == null) {
            return Collections.emptyList();
        }

        return relationships.stream().map(this::convertRelationship).collect(Collectors.toList());
    }

    public Relationship convertRelationship(org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.Relationship rel) {
        Relationship relationship = new Relationship();

        relationship.setGuid(rel.getGUID());
        relationship.setCreatedBy(rel.getCreatedBy());
        relationship.setCreateTime(rel.getCreateTime());

        relationship.setUpdatedBy(rel.getUpdatedBy());
        relationship.setUpdateTime(rel.getUpdateTime());

        relationship.setVersion(rel.getVersion());
        if (rel.getStatus() != null && rel.getStatus().getName() != null) {
            relationship.setStatus(rel.getStatus().getName());
        }

        if (rel.getType() != null && rel.getType().getTypeDefName() != null) {
            relationship.setType(convertInstanceType(rel.getType()));
        }

        if (rel.getEntityOneProxy() != null) {
            relationship.setFromEntity(getElement(rel.getEntityOneProxy()));
        }
        if (rel.getEntityTwoProxy() != null) {
            relationship.setToEntity(getElement(rel.getEntityTwoProxy()));
        }

        return relationship;
    }

    public List<Classification> convertClassifications
            (List<org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.Classification> classificationsFromEntity) {

        if (classificationsFromEntity == null || classificationsFromEntity.isEmpty()) {
            return new ArrayList<>();
        }

        List<Classification> classifications = new ArrayList<>(classificationsFromEntity.size());
        for (org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.Classification classificationEntity : classificationsFromEntity) {
            Classification classification = new Classification();
            classification.setName(classificationEntity.getName());
            if (classificationEntity.getClassificationOrigin() != null && classificationEntity.getClassificationOrigin().getDescription() != null) {
                classification.setOrigin(classificationEntity.getClassificationOrigin().getDescription());
            }
            classification.setOriginGUID(classificationEntity.getClassificationOriginGUID());

            classification.setCreatedBy(classificationEntity.getCreatedBy());
            classification.setCreateTime(classificationEntity.getCreateTime());

            classification.setUpdatedBy(classificationEntity.getUpdatedBy());
            classification.setUpdateTime(classificationEntity.getUpdateTime());

            classification.setVersion(classificationEntity.getVersion());
            if (classificationEntity.getStatus() != null) {
                classification.setStatus(classificationEntity.getStatus().getName());
            }
            if (classificationEntity.getType() != null) {
                classification.setType(convertInstanceType(classificationEntity.getType()));
            }
            if (classificationEntity.getProperties() != null) {
                classification.setProperties(repositoryHelper.getInstancePropertiesAsMap(classificationEntity.getProperties()));
            }

            classifications.add(classification);
        }

        return classifications;
    }

    public Type convertType(TypeDef openType) {
        Type type = new Type();
        type.setName(openType.getName());
        type.setDescription(openType.getDescription());
        type.setVersion(openType.getVersion());
        type.setSuperType(openType.getSuperType().getName());
        return type;
    }

    public AssetElements buildAssetElements(EntityDetail entityDetail) {
        if (entityDetail == null) {
            return null;
        }

        AssetElements element = new AssetElements();
        element.setGuid(entityDetail.getGUID());
        element.setType(convertInstanceType(entityDetail.getType()));
        element.setProperties(repositoryHelper.getInstancePropertiesAsMap(entityDetail.getProperties()));

        return element;
    }

    Type convertInstanceType(InstanceType instanceType) {
        Type type = new Type();
        type.setName(instanceType.getTypeDefName());
        type.setDescription(instanceType.getTypeDefDescription());
        type.setVersion(instanceType.getTypeDefVersion());
        return type;
    }

    private Element getElement(EntityProxy entityProxy) {
        String method = "getAsset";
        Element asset = new Element();

        asset.setGuid(entityProxy.getGUID());
        if (entityProxy.getUniqueProperties() != null) {
            asset.setName(repositoryHelper.getStringProperty("userID", Constants.NAME, entityProxy.getUniqueProperties(), method));
        }
        asset.setMetadataCollectionId(entityProxy.getMetadataCollectionId());
        asset.setCreatedBy(entityProxy.getCreatedBy());
        asset.setCreateTime(entityProxy.getCreateTime());
        asset.setUpdatedBy(entityProxy.getUpdatedBy());
        asset.setUpdateTime(entityProxy.getUpdateTime());
        asset.setStatus(entityProxy.getStatus().getName());
        asset.setVersion(entityProxy.getVersion());
        asset.setType(convertInstanceType(entityProxy.getType()));

        return asset;
    }

    private Element lastElementAdded(Element tree) {
        List<Element> innerElement = tree.getParentElement();
        if (innerElement == null) {
            return tree;
        }
        return lastElementAdded(innerElement.get(innerElement.size() - 1));
    }

    public void addElement(AssetElement assetElement, EntityDetail entityDetail) {
        List<Element> context = assetElement.getContext();
        List<Element> elements = new ArrayList<>();
        elements.add(buildAssetElements(entityDetail));

        if (context != null) {
            Element leaf = lastElementAdded(context.get(context.size() - 1));
            leaf.setParentElement(elements);
        } else {
            assetElement.setContext(elements);
        }
    }

    public Element getLastNode(AssetElement assetElement) {
        List<Element> context = assetElement.getContext();

        return CollectionUtils.isNotEmpty(context) ? lastElementAdded(context.get(context.size() - 1)) : null;
    }

    public void addChildElement(Element parentElement, List<Element> elements) {
        if (parentElement != null) {
            if (parentElement.getParentElement() != null) {
                parentElement.getParentElement().addAll(elements);
            } else {
                parentElement.setParentElement(elements);
            }
        }
    }

    public void addContextElement(AssetElement assetElement, EntityDetail entityDetail) {
        List<Element> context = assetElement.getContext();
        if (context == null) {
            context = new ArrayList<>();
        }
        context.add(buildAssetElements(entityDetail));
        assetElement.setContext(context);
    }

}
