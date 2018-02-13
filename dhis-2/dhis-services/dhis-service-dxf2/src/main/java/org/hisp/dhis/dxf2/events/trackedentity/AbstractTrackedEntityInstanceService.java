package org.hisp.dhis.dxf2.events.trackedentity;

/*
 * Copyright (c) 2004-2018, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.hisp.dhis.common.CodeGenerator;
import org.hisp.dhis.common.IdSchemes;
import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.common.IllegalQueryException;
import org.hisp.dhis.commons.collection.CachingMap;
import org.hisp.dhis.dbms.DbmsManager;
import org.hisp.dhis.dxf2.common.ImportOptions;
import org.hisp.dhis.dxf2.events.TrackedEntityInstanceParams;
import org.hisp.dhis.dxf2.events.TrackerAccessManager;
import org.hisp.dhis.dxf2.events.enrollment.Enrollment;
import org.hisp.dhis.dxf2.events.enrollment.EnrollmentService;
import org.hisp.dhis.dxf2.importsummary.ImportConflict;
import org.hisp.dhis.dxf2.importsummary.ImportStatus;
import org.hisp.dhis.dxf2.importsummary.ImportSummaries;
import org.hisp.dhis.dxf2.importsummary.ImportSummary;
import org.hisp.dhis.fileresource.FileResource;
import org.hisp.dhis.fileresource.FileResourceService;
import org.hisp.dhis.importexport.ImportStrategy;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.program.ProgramInstance;
import org.hisp.dhis.program.ProgramInstanceService;
import org.hisp.dhis.query.Query;
import org.hisp.dhis.query.QueryService;
import org.hisp.dhis.query.Restrictions;
import org.hisp.dhis.relationship.Relationship;
import org.hisp.dhis.relationship.RelationshipService;
import org.hisp.dhis.relationship.RelationshipType;
import org.hisp.dhis.schema.SchemaService;
import org.hisp.dhis.system.util.DateUtils;
import org.hisp.dhis.trackedentity.TrackedEntityAttribute;
import org.hisp.dhis.trackedentity.TrackedEntityAttributeService;
import org.hisp.dhis.trackedentity.TrackedEntityInstanceQueryParams;
import org.hisp.dhis.trackedentity.TrackedEntityType;
import org.hisp.dhis.trackedentityattributevalue.TrackedEntityAttributeValue;
import org.hisp.dhis.trackedentityattributevalue.TrackedEntityAttributeValueService;
import org.hisp.dhis.user.CurrentUserService;
import org.hisp.dhis.user.User;
import org.hisp.dhis.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Morten Olav Hansen <mortenoh@gmail.com>
 */
public abstract class AbstractTrackedEntityInstanceService
    implements TrackedEntityInstanceService
{
    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    @Autowired
    protected org.hisp.dhis.trackedentity.TrackedEntityInstanceService teiService;

    @Autowired
    protected TrackedEntityAttributeService trackedEntityAttributeService;

    @Autowired
    protected RelationshipService relationshipService;

    @Autowired
    protected TrackedEntityAttributeValueService trackedEntityAttributeValueService;

    @Autowired
    protected org.hisp.dhis.trackedentity.TrackedEntityInstanceService entityInstanceService;

    @Autowired
    protected IdentifiableObjectManager manager;

    @Autowired
    protected UserService userService;

    @Autowired
    protected DbmsManager dbmsManager;

    @Autowired
    protected EnrollmentService enrollmentService;

    @Autowired
    protected ProgramInstanceService programInstanceService;

    @Autowired
    protected CurrentUserService currentUserService;

    @Autowired
    protected SchemaService schemaService;

    @Autowired
    protected QueryService queryService;

    @Autowired
    protected TrackerAccessManager trackerAccessManager;

    @Autowired
    protected FileResourceService fileResourceService;

    private final CachingMap<String, OrganisationUnit> organisationUnitCache = new CachingMap<>();

    private final CachingMap<String, TrackedEntityType> trackedEntityCache = new CachingMap<>();

    private final CachingMap<String, TrackedEntityAttribute> trackedEntityAttributeCache = new CachingMap<>();

    // -------------------------------------------------------------------------
    // READ
    // -------------------------------------------------------------------------

    @Override
    public List<TrackedEntityInstance> getTrackedEntityInstances( TrackedEntityInstanceQueryParams queryParams, TrackedEntityInstanceParams params )
    {
        List<org.hisp.dhis.trackedentity.TrackedEntityInstance> teis = entityInstanceService.getTrackedEntityInstances( queryParams );
        List<TrackedEntityInstance> teiItems = new ArrayList<>();
        User user = currentUserService.getCurrentUser();

        for ( org.hisp.dhis.trackedentity.TrackedEntityInstance trackedEntityInstance : teis )
        {
            teiItems.add( getTrackedEntityInstance( trackedEntityInstance, params, user ) );
        }

        return teiItems;
    }

    @Override
    public int getTrackedEntityInstanceCount( TrackedEntityInstanceQueryParams params, boolean sync )
    {
        return entityInstanceService.getTrackedEntityInstanceCount( params, sync );
    }

    @Override
    public TrackedEntityInstance getTrackedEntityInstance( String uid )
    {
        return getTrackedEntityInstance( teiService.getTrackedEntityInstance( uid ) );
    }

    @Override
    public TrackedEntityInstance getTrackedEntityInstance( String uid, TrackedEntityInstanceParams params )
    {
        return getTrackedEntityInstance( teiService.getTrackedEntityInstance( uid ), params );
    }

    @Override
    public TrackedEntityInstance getTrackedEntityInstance( org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstance )
    {
        return getTrackedEntityInstance( entityInstance, TrackedEntityInstanceParams.TRUE );
    }

    @Override
    public TrackedEntityInstance getTrackedEntityInstance( org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstance,
        TrackedEntityInstanceParams params )
    {
        return getTrackedEntityInstance( entityInstance, params, currentUserService.getCurrentUser() );
    }

    @Override
    public TrackedEntityInstance getTrackedEntityInstance( org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstance,
        TrackedEntityInstanceParams params, User user )
    {
        if ( entityInstance == null )
        {
            return null;
        }

        List<String> errors = trackerAccessManager.canRead( user, entityInstance );

        if ( !errors.isEmpty() )
        {
            throw new IllegalQueryException( errors.toString() );
        }

        TrackedEntityInstance trackedEntityInstance = new TrackedEntityInstance();
        trackedEntityInstance.setTrackedEntityInstance( entityInstance.getUid() );
        trackedEntityInstance.setOrgUnit( entityInstance.getOrganisationUnit().getUid() );
        trackedEntityInstance.setTrackedEntityType( entityInstance.getTrackedEntityType().getUid() );
        trackedEntityInstance.setCreated( DateUtils.getIso8601NoTz( entityInstance.getCreated() ) );
        trackedEntityInstance.setCreatedAtClient( DateUtils.getIso8601NoTz( entityInstance.getLastUpdatedAtClient() ) );
        trackedEntityInstance.setLastUpdated( DateUtils.getIso8601NoTz( entityInstance.getLastUpdated() ) );
        trackedEntityInstance.setLastUpdatedAtClient( DateUtils.getIso8601NoTz( entityInstance.getLastUpdatedAtClient() ) );
        trackedEntityInstance.setInactive( entityInstance.isInactive() );
        trackedEntityInstance.setFeatureType( entityInstance.getFeatureType() );
        trackedEntityInstance.setCoordinates( entityInstance.getCoordinates() );

        if ( params.isIncludeRelationships() )
        {
            //TODO include relationships in data model and void transactional query in for-loop

            Collection<Relationship> relationships = relationshipService.getRelationshipsForTrackedEntityInstance( entityInstance );

            for ( Relationship entityRelationship : relationships )
            {
                org.hisp.dhis.dxf2.events.trackedentity.Relationship relationship = new org.hisp.dhis.dxf2.events.trackedentity.Relationship();
                relationship.setDisplayName( entityRelationship.getRelationshipType().getDisplayName() );
                relationship.setTrackedEntityInstanceA( entityRelationship.getEntityInstanceA().getUid() );
                relationship.setTrackedEntityInstanceB( entityRelationship.getEntityInstanceB().getUid() );

                relationship.setRelationship( entityRelationship.getRelationshipType().getUid() );

                // we might have cases where A <=> A, so we only include the relative if the UIDs do not match
                if ( !entityRelationship.getEntityInstanceA().getUid().equals( entityInstance.getUid() ) )
                {
                    relationship.setRelative( getTrackedEntityInstance( entityRelationship.getEntityInstanceA(), TrackedEntityInstanceParams.FALSE ) );
                }
                else if ( !entityRelationship.getEntityInstanceB().getUid().equals( entityInstance.getUid() ) )
                {
                    relationship.setRelative( getTrackedEntityInstance( entityRelationship.getEntityInstanceB(), TrackedEntityInstanceParams.FALSE ) );
                }

                trackedEntityInstance.getRelationships().add( relationship );
            }
        }

        if ( params.isIncludeEnrollments() )
        {
            for ( ProgramInstance programInstance : entityInstance.getProgramInstances() )
            {
                trackedEntityInstance.getEnrollments().add( enrollmentService.getEnrollment( programInstance, params ) );
            }
        }

        Set<TrackedEntityAttribute> readableAttributes = trackedEntityAttributeService.getAllUserReadableTrackedEntityAttributes();

        for ( TrackedEntityAttributeValue attributeValue : entityInstance.getTrackedEntityAttributeValues() )
        {
            if ( readableAttributes.contains( attributeValue.getAttribute() ) )
            {
                Attribute attribute = new Attribute();

                attribute.setCreated( DateUtils.getIso8601NoTz( attributeValue.getCreated() ) );
                attribute.setLastUpdated( DateUtils.getIso8601NoTz( attributeValue.getLastUpdated() ) );
                attribute.setDisplayName( attributeValue.getAttribute().getDisplayName() );
                attribute.setAttribute( attributeValue.getAttribute().getUid() );
                attribute.setValueType( attributeValue.getAttribute().getValueType() );
                attribute.setCode( attributeValue.getAttribute().getCode() );
                attribute.setValue( attributeValue.getValue() );
                attribute.setStoredBy( attributeValue.getStoredBy() );

                trackedEntityInstance.getAttributes().add( attribute );
            }
        }

        return trackedEntityInstance;
    }

    public org.hisp.dhis.trackedentity.TrackedEntityInstance getTrackedEntityInstance( TrackedEntityInstance trackedEntityInstance, ImportOptions importOptions, ImportSummary importSummary )
    {
        if ( StringUtils.isEmpty( trackedEntityInstance.getOrgUnit() ) )
        {
            importSummary.getConflicts().add( new ImportConflict( trackedEntityInstance.getTrackedEntityInstance(), "No org unit ID in tracked entity instance object." ) );
            return null;
        }

        org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstance = new org.hisp.dhis.trackedentity.TrackedEntityInstance();

        OrganisationUnit organisationUnit = getOrganisationUnit( importOptions.getIdSchemes(), trackedEntityInstance.getOrgUnit() );

        if ( organisationUnit == null )
        {
            importSummary.getConflicts().add( new ImportConflict( trackedEntityInstance.getTrackedEntityInstance(), "Invalid org unit ID: " + trackedEntityInstance.getOrgUnit() ) );
            return null;
        }

        entityInstance.setOrganisationUnit( organisationUnit );

        TrackedEntityType trackedEntityType = getTrackedEntityType( importOptions.getIdSchemes(), trackedEntityInstance.getTrackedEntityType() );

        if ( trackedEntityType == null )
        {
            importSummary.getConflicts().add( new ImportConflict( trackedEntityInstance.getTrackedEntityInstance(), "Invalid tracked entity ID: " + trackedEntityInstance.getTrackedEntityType() ) );
            return null;
        }

        entityInstance.setTrackedEntityType( trackedEntityType );
        entityInstance.setUid( CodeGenerator.isValidUid( trackedEntityInstance.getTrackedEntityInstance() ) ?
            trackedEntityInstance.getTrackedEntityInstance() : CodeGenerator.generateUid() );

        return entityInstance;
    }

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------

    @Override
    public ImportSummaries addTrackedEntityInstances( List<TrackedEntityInstance> trackedEntityInstances, ImportOptions importOptions )
    {
        if ( importOptions == null )
        {
            importOptions = new ImportOptions();
        }

        User user = currentUserService.getCurrentUser();
        List<List<TrackedEntityInstance>> partitions = Lists.partition( trackedEntityInstances, FLUSH_FREQUENCY );

        ImportSummaries importSummaries = new ImportSummaries();

        for ( List<TrackedEntityInstance> _trackedEntityInstances : partitions )
        {
            // prepare caches
            Collection<String> orgUnits = _trackedEntityInstances.stream().map( TrackedEntityInstance::getOrgUnit ).collect( Collectors.toSet() );

            if ( !orgUnits.isEmpty() )
            {
                Query query = Query.from( schemaService.getDynamicSchema( OrganisationUnit.class ) );
                query.setUser( user );
                query.add( Restrictions.in( "id", orgUnits ) );
                queryService.query( query ).forEach( ou -> organisationUnitCache.put( ou.getUid(), (OrganisationUnit) ou ) );
            }

            Collection<String> trackedEntityAttributes = new HashSet<>();
            _trackedEntityInstances.forEach( e -> e.getAttributes().forEach( at -> trackedEntityAttributes.add( at.getAttribute() ) ) );

            if ( !trackedEntityAttributes.isEmpty() )
            {
                Query query = Query.from( schemaService.getDynamicSchema( TrackedEntityAttribute.class ) );
                query.setUser( user );
                query.add( Restrictions.in( "id", trackedEntityAttributes ) );
                queryService.query( query ).forEach( tea -> trackedEntityAttributeCache.put( tea.getUid(), (TrackedEntityAttribute) tea ) );
            }

            for ( TrackedEntityInstance trackedEntityInstance : _trackedEntityInstances )
            {
                importSummaries.addImportSummary( addTrackedEntityInstance( trackedEntityInstance, user, importOptions ) );
            }

            clearSession();
        }

        return importSummaries;
    }

    @Override
    public ImportSummary addTrackedEntityInstance( TrackedEntityInstance trackedEntityInstance, ImportOptions importOptions )
    {
        return addTrackedEntityInstance( trackedEntityInstance, currentUserService.getCurrentUser(), importOptions );
    }

    private ImportSummary addTrackedEntityInstance( TrackedEntityInstance trackedEntityInstance, User user, ImportOptions importOptions )
    {
        if ( importOptions == null )
        {
            importOptions = new ImportOptions();
        }

        ImportSummary importSummary = new ImportSummary( trackedEntityInstance.getTrackedEntityInstance() );

        trackedEntityInstance.trimValuesToNull();

        Set<ImportConflict> importConflicts = new HashSet<>();
        importConflicts.addAll( checkTrackedEntityType( trackedEntityInstance, importOptions ) );
        importConflicts.addAll( checkAttributes( trackedEntityInstance, importOptions ) );

        importSummary.setConflicts( importConflicts );

        if ( !importConflicts.isEmpty() )
        {
            importSummary.setStatus( ImportStatus.ERROR );
            importSummary.getImportCount().incrementIgnored();
            return importSummary;
        }

        org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstance = getTrackedEntityInstance( trackedEntityInstance, importOptions, importSummary );

        if ( entityInstance == null )
        {
            return importSummary;
        }

        List<String> errors = trackerAccessManager.canWrite( user, entityInstance );

        if ( !errors.isEmpty() )
        {
            return new ImportSummary( ImportStatus.ERROR, errors.toString() );
        }

        teiService.addTrackedEntityInstance( entityInstance );

        updateRelationships( trackedEntityInstance, entityInstance );
        updateAttributeValues( trackedEntityInstance, entityInstance, user );
        updateDateFields( trackedEntityInstance, entityInstance );

        entityInstance.setFeatureType( trackedEntityInstance.getFeatureType() );
        entityInstance.setCoordinates( trackedEntityInstance.getCoordinates() );

        teiService.updateTrackedEntityInstance( entityInstance );

        importSummary.setReference( entityInstance.getUid() );
        importSummary.getImportCount().incrementImported();

        importOptions.setStrategy( ImportStrategy.CREATE_AND_UPDATE );
        importSummary.setEnrollments( handleEnrollments( trackedEntityInstance, entityInstance, importOptions ) );

        return importSummary;
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------

    @Override
    public ImportSummaries updateTrackedEntityInstances( List<TrackedEntityInstance> trackedEntityInstances, ImportOptions importOptions )
    {
        if ( importOptions == null )
        {
            importOptions = new ImportOptions();
        }

        User user = currentUserService.getCurrentUser();
        List<List<TrackedEntityInstance>> partitions = Lists.partition( trackedEntityInstances, FLUSH_FREQUENCY );

        ImportSummaries importSummaries = new ImportSummaries();

        for ( List<TrackedEntityInstance> _trackedEntityInstances : partitions )
        {
            // prepare caches
            Collection<String> orgUnits = _trackedEntityInstances.stream().map( TrackedEntityInstance::getOrgUnit ).collect( Collectors.toSet() );

            if ( !orgUnits.isEmpty() )
            {
                Query query = Query.from( schemaService.getDynamicSchema( OrganisationUnit.class ) );
                query.setUser( user );
                query.add( Restrictions.in( "id", orgUnits ) );
                queryService.query( query ).forEach( ou -> organisationUnitCache.put( ou.getUid(), (OrganisationUnit) ou ) );
            }

            Collection<String> trackedEntityAttributes = new HashSet<>();
            _trackedEntityInstances.forEach( e -> e.getAttributes().forEach( at -> trackedEntityAttributes.add( at.getAttribute() ) ) );

            if ( !trackedEntityAttributes.isEmpty() )
            {
                Query query = Query.from( schemaService.getDynamicSchema( TrackedEntityAttribute.class ) );
                query.setUser( user );
                query.add( Restrictions.in( "id", trackedEntityAttributes ) );
                queryService.query( query ).forEach( tea -> trackedEntityAttributeCache.put( tea.getUid(), (TrackedEntityAttribute) tea ) );
            }

            for ( TrackedEntityInstance trackedEntityInstance : _trackedEntityInstances )
            {
                importSummaries.addImportSummary( updateTrackedEntityInstance( trackedEntityInstance, user, importOptions ) );
            }

            clearSession();
        }

        return importSummaries;
    }

    @Override
    public ImportSummary updateTrackedEntityInstance( TrackedEntityInstance trackedEntityInstance, ImportOptions importOptions )
    {
        return updateTrackedEntityInstance( trackedEntityInstance, currentUserService.getCurrentUser(), importOptions );
    }

    private ImportSummary updateTrackedEntityInstance( TrackedEntityInstance trackedEntityInstance, User user, ImportOptions importOptions )
    {
        if ( importOptions == null )
        {
            importOptions = new ImportOptions();
        }

        ImportSummary importSummary = new ImportSummary( trackedEntityInstance.getTrackedEntityInstance() );

        trackedEntityInstance.trimValuesToNull();

        Set<ImportConflict> importConflicts = new HashSet<>();
        importConflicts.addAll( checkRelationships( trackedEntityInstance ) );
        importConflicts.addAll( checkAttributes( trackedEntityInstance, importOptions ) );

        org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstance = manager.get( org.hisp.dhis.trackedentity.TrackedEntityInstance.class,
            trackedEntityInstance.getTrackedEntityInstance() );

        if ( entityInstance == null )
        {
            importConflicts.add( new ImportConflict( "TrackedEntityInstance", "trackedEntityInstance " + trackedEntityInstance.getTrackedEntityInstance()
                + " does not point to valid trackedEntityInstance" ) );
        }

        List<String> errors = trackerAccessManager.canWrite( user, entityInstance );

        if ( !errors.isEmpty() )
        {
            return new ImportSummary( ImportStatus.ERROR, errors.toString() );
        }

        OrganisationUnit organisationUnit = getOrganisationUnit( new IdSchemes(), trackedEntityInstance.getOrgUnit() );

        if ( organisationUnit == null )
        {
            importConflicts.add( new ImportConflict( "OrganisationUnit", "orgUnit " + trackedEntityInstance.getOrgUnit()
                + " does not point to valid organisation unit" ) );
        }
        else
        {
            entityInstance.setOrganisationUnit( organisationUnit );
        }

        importSummary.setConflicts( importConflicts );

        if ( !importConflicts.isEmpty() )
        {
            importSummary.setStatus( ImportStatus.ERROR );
            importSummary.getImportCount().incrementIgnored();

            return importSummary;
        }

        entityInstance.setInactive( trackedEntityInstance.isInactive() );
        entityInstance.setFeatureType( trackedEntityInstance.getFeatureType() );
        entityInstance.setCoordinates( trackedEntityInstance.getCoordinates() );

        removeRelationships( entityInstance );
        removeAttributeValues( entityInstance );
        teiService.updateTrackedEntityInstance( entityInstance );

        updateRelationships( trackedEntityInstance, entityInstance );
        updateAttributeValues( trackedEntityInstance, entityInstance, user );
        updateDateFields( trackedEntityInstance, entityInstance );

        teiService.updateTrackedEntityInstance( entityInstance );

        importSummary.setStatus( ImportStatus.SUCCESS );
        importSummary.setReference( entityInstance.getUid() );
        importSummary.getImportCount().incrementUpdated();

        importOptions.setStrategy( ImportStrategy.CREATE_AND_UPDATE );
        importSummary.setEnrollments( handleEnrollments( trackedEntityInstance, entityInstance, importOptions ) );

        return importSummary;
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------

    @Override
    public ImportSummary deleteTrackedEntityInstance( String uid )
    {
        org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstance = teiService.getTrackedEntityInstance( uid );

        if ( entityInstance != null )
        {
            teiService.deleteTrackedEntityInstance( entityInstance );
            return new ImportSummary( ImportStatus.SUCCESS, "Deletion of tracked entity instance " + uid + " was successful" ).incrementDeleted();
        }

        return new ImportSummary( ImportStatus.ERROR, "ID " + uid + " does not point to a valid tracked entity instance" ).incrementIgnored();
    }

    @Override
    public ImportSummaries deleteTrackedEntityInstances( List<String> uids )
    {
        ImportSummaries importSummaries = new ImportSummaries();
        int counter = 0;

        for ( String uid : uids )
        {
            importSummaries.addImportSummary( deleteTrackedEntityInstance( uid ) );

            if ( counter % FLUSH_FREQUENCY == 0 )
            {
                clearSession();
            }

            counter++;
        }

        return importSummaries;
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    private ImportSummaries handleEnrollments( TrackedEntityInstance trackedEntityInstanceDTO, org.hisp.dhis.trackedentity.TrackedEntityInstance trackedEntityInstance, ImportOptions importOptions )
    {
        List<Enrollment> create = new ArrayList<>();
        List<Enrollment> update = new ArrayList<>();

        for ( Enrollment enrollment : trackedEntityInstanceDTO.getEnrollments() )
        {
            enrollment.setTrackedEntityType( trackedEntityInstanceDTO.getTrackedEntityType() );
            enrollment.setTrackedEntityInstance( trackedEntityInstance.getUid() );

            if ( !programInstanceService.programInstanceExists( enrollment.getEnrollment() ) )
            {
                create.add( enrollment );
            }
            else
            {
                update.add( enrollment );
            }
        }

        ImportSummaries importSummaries = new ImportSummaries();
        importSummaries.addImportSummaries( enrollmentService.addEnrollments( create, importOptions ) );
        importSummaries.addImportSummaries( enrollmentService.updateEnrollments( update, importOptions ) );

        return importSummaries;
    }

    private void updateAttributeValues( TrackedEntityInstance trackedEntityInstance,
        org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstance, User user )
    {
        for ( Attribute attribute : trackedEntityInstance.getAttributes() )
        {
            TrackedEntityAttribute entityAttribute = manager.get( TrackedEntityAttribute.class,
                attribute.getAttribute() );

            if ( entityAttribute != null )
            {
                TrackedEntityAttributeValue attributeValue = new TrackedEntityAttributeValue();
                attributeValue.setEntityInstance( entityInstance );
                attributeValue.setValue( attribute.getValue() );
                attributeValue.setAttribute( entityAttribute );

                String storedBy = getStoredBy( attributeValue, new ImportSummary(), user );
                attributeValue.setStoredBy( storedBy );

                trackedEntityAttributeValueService.addTrackedEntityAttributeValue( attributeValue );
            }
        }
    }

    private void updateRelationships( TrackedEntityInstance trackedEntityInstance, org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstance )
    {
        for ( org.hisp.dhis.dxf2.events.trackedentity.Relationship relationship : trackedEntityInstance.getRelationships() )
        {
            org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstanceA = manager.get( org.hisp.dhis.trackedentity.TrackedEntityInstance.class, relationship.getTrackedEntityInstanceA() );
            org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstanceB = manager.get( org.hisp.dhis.trackedentity.TrackedEntityInstance.class, relationship.getTrackedEntityInstanceB() );

            RelationshipType relationshipType = manager.get( RelationshipType.class, relationship.getRelationship() );

            Relationship entityRelationship = new Relationship();
            entityRelationship.setEntityInstanceA( entityInstanceA );
            entityRelationship.setEntityInstanceB( entityInstanceB );
            entityRelationship.setRelationshipType( relationshipType );

            relationshipService.addRelationship( entityRelationship );
        }
    }

    private void removeRelationships( org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstance )
    {
        Collection<Relationship> relationships = relationshipService.getRelationshipsForTrackedEntityInstance( entityInstance );
        relationships.forEach( relationshipService::deleteRelationship );
    }

    private void removeAttributeValues( org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstance )
    {
        entityInstance.getTrackedEntityAttributeValues().forEach( trackedEntityAttributeValueService::deleteTrackedEntityAttributeValue );
        teiService.updateTrackedEntityInstance( entityInstance );
    }

    private OrganisationUnit getOrganisationUnit( IdSchemes idSchemes, String id )
    {
        return organisationUnitCache.get( id, () -> manager.getObject( OrganisationUnit.class, idSchemes.getOrgUnitIdScheme(), id ) );
    }

    private TrackedEntityType getTrackedEntityType( IdSchemes idSchemes, String id )
    {
        return trackedEntityCache.get( id, () -> manager.getObject( TrackedEntityType.class, idSchemes.getTrackedEntityIdScheme(), id ) );
    }

    private TrackedEntityAttribute getTrackedEntityAttribute( IdSchemes idSchemes, String id )
    {
        return trackedEntityAttributeCache.get( id, () -> manager.getObject( TrackedEntityAttribute.class, idSchemes.getTrackedEntityAttributeIdScheme(), id ) );
    }

    //--------------------------------------------------------------------------
    // VALIDATION
    //--------------------------------------------------------------------------

    private List<ImportConflict> validateAttributeType( Attribute attribute, ImportOptions importOptions )
    {
        List<ImportConflict> importConflicts = Lists.newArrayList();

        if ( attribute == null || attribute.getValue() == null )
        {
            return importConflicts;
        }

        TrackedEntityAttribute trackedEntityAttribute = getTrackedEntityAttribute( importOptions.getIdSchemes(), attribute.getAttribute() );

        if ( trackedEntityAttribute == null )
        {
            importConflicts.add( new ImportConflict( "Attribute.attribute", "Does not point to a valid attribute." ) );
            return importConflicts;
        }

        String errorMessage = trackedEntityAttributeService.validateValueType( trackedEntityAttribute, attribute.getValue() );

        if ( errorMessage != null )
        {
            importConflicts.add( new ImportConflict( "Attribute.value", errorMessage ) );
        }

        return importConflicts;
    }

    private List<ImportConflict> checkRelationships( TrackedEntityInstance trackedEntityInstance )
    {
        List<ImportConflict> importConflicts = new ArrayList<>();

        for ( org.hisp.dhis.dxf2.events.trackedentity.Relationship relationship : trackedEntityInstance.getRelationships() )
        {
            RelationshipType relationshipType = manager.get( RelationshipType.class, relationship.getRelationship() );

            if ( relationshipType == null )
            {
                importConflicts.add( new ImportConflict( "Relationship.type", "Invalid type " + relationship.getRelationship() ) );
            }

            org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstanceA = manager.get( org.hisp.dhis.trackedentity.TrackedEntityInstance.class, relationship.getTrackedEntityInstanceA() );

            if ( entityInstanceA == null )
            {
                importConflicts.add( new ImportConflict( "Relationship.trackedEntityInstance", "Invalid trackedEntityInstance "
                    + relationship.getTrackedEntityInstanceA() ) );
            }

            org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstanceB = manager.get( org.hisp.dhis.trackedentity.TrackedEntityInstance.class, relationship.getTrackedEntityInstanceB() );

            if ( entityInstanceB == null )
            {
                importConflicts.add( new ImportConflict( "Relationship.trackedEntityInstance", "Invalid trackedEntityInstance "
                    + relationship.getTrackedEntityInstanceB() ) );
            }
        }

        return importConflicts;
    }

    private List<ImportConflict> checkScope( org.hisp.dhis.trackedentity.TrackedEntityInstance trackedEntityInstance,
        TrackedEntityAttribute trackedEntityAttribute, String value, OrganisationUnit organisationUnit )
    {
        List<ImportConflict> importConflicts = new ArrayList<>();

        if ( trackedEntityAttribute == null || value == null )
        {
            return importConflicts;
        }

        String errorMessage = trackedEntityAttributeService.validateScope( trackedEntityAttribute, value, trackedEntityInstance,
            organisationUnit, null );

        if ( errorMessage != null )
        {
            importConflicts.add( new ImportConflict( "Attribute.value", errorMessage ) );
        }

        return importConflicts;
    }

    private List<ImportConflict> checkAttributes( TrackedEntityInstance trackedEntityInstance, ImportOptions importOptions )
    {
        List<ImportConflict> importConflicts = new ArrayList<>();
        List<String> fileValues = new ArrayList<>();

        org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstance = manager.get( org.hisp.dhis.trackedentity.TrackedEntityInstance.class,
            trackedEntityInstance.getTrackedEntityInstance() );

        if ( entityInstance != null )
        {
            entityInstance.getTrackedEntityAttributeValues().stream()
                .filter( attrVal -> attrVal.getAttribute().getValueType().isFile() )
                .forEach( attrVal -> fileValues.add( attrVal.getValue() ) );
        }

        for ( Attribute attribute : trackedEntityInstance.getAttributes() )
        {
            TrackedEntityAttribute entityAttribute = getTrackedEntityAttribute( importOptions.getIdSchemes(), attribute.getAttribute() );

            if ( entityAttribute == null )
            {
                importConflicts.add( new ImportConflict( "Attribute.attribute", "Invalid attribute " + attribute.getAttribute() ) );
                continue;
            }

            if ( entityAttribute.isUnique() )
            {
                OrganisationUnit organisationUnit = getOrganisationUnit( importOptions.getIdSchemes(), trackedEntityInstance.getOrgUnit() );
                org.hisp.dhis.trackedentity.TrackedEntityInstance tei = teiService.getTrackedEntityInstance( trackedEntityInstance.getTrackedEntityInstance() );
                importConflicts.addAll( checkScope( tei, entityAttribute, attribute.getValue(), organisationUnit ) );
            }

            importConflicts.addAll( validateAttributeType( attribute, importOptions ) );

            if ( entityAttribute.getValueType().isFile() && checkAssigned( attribute, fileValues ) )
            {
                importConflicts.add( new ImportConflict( "Attribute.value",
                    String.format( " File Resource with uid '%s' has already been assigned to a different object", attribute.getValue() ) ) );
            }

        }

        return importConflicts;
    }

    private List<ImportConflict> checkTrackedEntityType( TrackedEntityInstance trackedEntityInstance, ImportOptions importOptions )
    {
        List<ImportConflict> importConflicts = new ArrayList<>();

        if ( trackedEntityInstance.getTrackedEntityType() == null )
        {
            importConflicts.add( new ImportConflict( "TrackedEntityInstance.trackedEntityType", "Missing required property trackedEntityType" ) );
            return importConflicts;
        }

        TrackedEntityType trackedEntityType = getTrackedEntityType( importOptions.getIdSchemes(), trackedEntityInstance.getTrackedEntityType() );

        if ( trackedEntityType == null )
        {
            importConflicts.add( new ImportConflict( "TrackedEntityInstance.trackedEntityType", "Invalid trackedEntityType" +
                trackedEntityInstance.getTrackedEntityType() ) );
        }

        return importConflicts;
    }

    private void clearSession()
    {
        organisationUnitCache.clear();
        trackedEntityCache.clear();
        trackedEntityAttributeCache.clear();

        dbmsManager.clearSession();
    }

    private void updateDateFields( TrackedEntityInstance trackedEntityInstance, org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstance )
    {
        entityInstance.setAutoFields();

        Date createdAtClient = DateUtils.parseDate( trackedEntityInstance.getCreatedAtClient() );

        if ( createdAtClient != null )
        {
            entityInstance.setCreatedAtClient( createdAtClient );
        }

        String lastUpdatedAtClient = trackedEntityInstance.getLastUpdatedAtClient();

        if ( lastUpdatedAtClient != null )
        {
            entityInstance.setLastUpdatedAtClient( DateUtils.parseDate( lastUpdatedAtClient ) );
        }
    }

    private String getStoredBy( TrackedEntityAttributeValue attributeValue, ImportSummary importSummary, User fallbackUser )
    {
        String storedBy = attributeValue.getStoredBy();

        if ( StringUtils.isEmpty( storedBy ) )
        {
            storedBy = User.getSafeUsername( fallbackUser );
        }
        else if ( storedBy.length() >= 31 )
        {
            if ( importSummary != null )
            {
                importSummary.getConflicts().add( new ImportConflict( "stored by",
                    storedBy + " is more than 31 characters, using current username instead" ) );
            }

            storedBy = User.getSafeUsername( fallbackUser );
        }

        return storedBy;
    }

    private boolean checkAssigned( Attribute attribute, List<String> oldFileValues )
    {
        FileResource fileResource = fileResourceService.getFileResource( attribute.getValue() );
        return fileResource != null && fileResource.isAssigned() && !oldFileValues.contains( attribute.getValue() );
    }
}

