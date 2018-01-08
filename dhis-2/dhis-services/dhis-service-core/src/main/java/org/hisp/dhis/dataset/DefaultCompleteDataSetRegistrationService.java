package org.hisp.dhis.dataset;

/*
 * Copyright (c) 2004-2017, University of Oslo
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

import org.hisp.dhis.dataelement.DataElementCategoryOptionCombo;
import org.hisp.dhis.dataelement.DataElementCategoryService;
import org.hisp.dhis.dataelement.DataElementOperand;
import org.hisp.dhis.dataset.notifications.DataSetNotificationService;
import org.hisp.dhis.datavalue.DataValueService;
import org.hisp.dhis.message.MessageService;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.period.Period;
import org.hisp.dhis.trackedentity.DefaultTrackedEntityInstanceService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hisp.dhis.common.DimensionalItemObject;
import org.hisp.dhis.common.IllegalQueryException;
import org.hisp.dhis.common.Map4;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Lars Helge Overland
 * @version $Id$
 */
@Transactional
public class DefaultCompleteDataSetRegistrationService
    implements CompleteDataSetRegistrationService
{
    
    private static final Log log = LogFactory.getLog( DefaultTrackedEntityInstanceService.class );
    
    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    private CompleteDataSetRegistrationStore completeDataSetRegistrationStore;

    public void setCompleteDataSetRegistrationStore( CompleteDataSetRegistrationStore completeDataSetRegistrationStore )
    {
        this.completeDataSetRegistrationStore = completeDataSetRegistrationStore;
    }

    private MessageService messageService;

    public void setMessageService( MessageService messageService )
    {
        this.messageService = messageService;
    }

    private DataElementCategoryService categoryService;

    public void setCategoryService( DataElementCategoryService categoryService )
    {
        this.categoryService = categoryService;
    }

    private DataSetNotificationService dataSetNotificationService;

    public void setDataSetNotificationService( DataSetNotificationService dataSetNotificationService )
    {
        this.dataSetNotificationService = dataSetNotificationService;
    }
    
    private DataValueService dataValueService;
    
    public void setDataValueService( DataValueService dataValueService )
    {
        this.dataValueService = dataValueService;
    }

    // -------------------------------------------------------------------------
    // CompleteDataSetRegistrationService
    // -------------------------------------------------------------------------

    @Override
    public void saveCompleteDataSetRegistration( CompleteDataSetRegistration registration )
    {
        if ( registration.getAttributeOptionCombo() == null )
        {
            registration.setAttributeOptionCombo( categoryService.getDefaultDataElementCategoryOptionCombo() );
        }
        
        // ---------------------------------------------------------------------
        // Check if compulsory data element operands are filled
        // ---------------------------------------------------------------------
        
        if( registration.getDataSet().isCompulsoryFieldsCompleteOnly() )
        {            
            Set<OrganisationUnit> orgUnits = new HashSet<OrganisationUnit>();
            orgUnits.add( registration.getSource() );
            validateCompulsoryFileds( registration );
        }        

        completeDataSetRegistrationStore.saveCompleteDataSetRegistration( registration );
    }

    @Override
    public void saveCompleteDataSetRegistration( CompleteDataSetRegistration registration, boolean skipNotification )
    {
        saveCompleteDataSetRegistration( registration );

        if ( !skipNotification )
        {
            if ( registration.getDataSet() != null  && registration.getDataSet().isNotifyCompletingUser() )
            {
                messageService.sendCompletenessMessage( registration );
            }

            dataSetNotificationService.sendCompleteDataSetNotifications( registration );
        }
    }

    @Override
    public void saveCompleteDataSetRegistrations( List<CompleteDataSetRegistration> registrations, boolean skipNotification )
    {
        for ( CompleteDataSetRegistration registration : registrations )
        {
            saveCompleteDataSetRegistration( registration, skipNotification );
        }
    }

    @Override
    public void updateCompleteDataSetRegistration( CompleteDataSetRegistration registration )
    {
        completeDataSetRegistrationStore.updateCompleteDataSetRegistration( registration );
    }

    @Override
    public void deleteCompleteDataSetRegistration( CompleteDataSetRegistration registration )
    {
        completeDataSetRegistrationStore.deleteCompleteDataSetRegistration( registration );
    }

    @Override
    public void deleteCompleteDataSetRegistrations( List<CompleteDataSetRegistration> registrations )
    {
        for ( CompleteDataSetRegistration registration : registrations )
        {
            completeDataSetRegistrationStore.deleteCompleteDataSetRegistration( registration );
        }
    }

    @Override
    public CompleteDataSetRegistration getCompleteDataSetRegistration( DataSet dataSet, Period period,
        OrganisationUnit source, DataElementCategoryOptionCombo attributeOptionCombo )
    {
        return completeDataSetRegistrationStore
            .getCompleteDataSetRegistration( dataSet, period, source, attributeOptionCombo );
    }

    @Override
    public List<CompleteDataSetRegistration> getAllCompleteDataSetRegistrations()
    {
        return completeDataSetRegistrationStore.getAllCompleteDataSetRegistrations();
    }

    @Override
    public List<CompleteDataSetRegistration> getCompleteDataSetRegistrations(
        Collection<DataSet> dataSets, Collection<OrganisationUnit> sources, Collection<Period> periods )
    {
        return completeDataSetRegistrationStore.getCompleteDataSetRegistrations( dataSets, sources, periods );
    }

    @Override
    public void deleteCompleteDataSetRegistrations( DataSet dataSet )
    {
        completeDataSetRegistrationStore.deleteCompleteDataSetRegistrations( dataSet );
    }

    @Override
    public void deleteCompleteDataSetRegistrations( OrganisationUnit unit )
    {
        completeDataSetRegistrationStore.deleteCompleteDataSetRegistrations( unit );
    }

    @Override
    public void validateCompulsoryFileds( CompleteDataSetRegistration registration ) throws IllegalQueryException
    {
        if( registration == null || 
            registration.getDataSet() == null || 
            registration.getPeriod() == null || 
            registration.getSource() == null || 
            registration.getAttributeOptionCombo() == null )
        {
            throw new IllegalQueryException( "Invalid CompleteDataSetRegistration parameter(s)." );            
        }
        
        String violation = null;        
        
        if( !registration.getDataSet().getCompulsoryDataElementOperands().isEmpty() )
        {
            List<Period> periods = new ArrayList<>();        
            periods.add( registration.getPeriod() );
            
            List<OrganisationUnit> organisationUnits = new ArrayList<>();        
            organisationUnits.add( registration.getSource() );
            
            Map4<OrganisationUnit, Period, String, DimensionalItemObject, Double> dataValues = new Map4<>();
            
            dataValues = dataValueService.getDataElementOperandValues( registration.getDataSet().getCompulsoryDataElementOperands(), periods, organisationUnits );
            
            if( dataValues.isEmpty() )
            {
                violation = "Compulsory data elements need to be filled before completing the data set.";
            }
            else 
            {
                for( DataElementOperand dataElementOperand : registration.getDataSet().getCompulsoryDataElementOperands() )
                {                                        
                    if( dataValues.getValue( registration.getSource(), registration.getPeriod(), registration.getAttributeOptionCombo().getUid(), dataElementOperand ) == null )
                    {
                        violation = "Compulsory data elements need to be filled before completing the data set.";
                        break;
                    }
                }
                
            }
            
        }        
        
        if ( violation != null )
        {
            log.warn( "Validation failed: " + violation );

            throw new IllegalQueryException( violation );
        }
        
    }
}
