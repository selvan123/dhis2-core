package org.hisp.dhis.analytics;

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

import com.google.common.base.MoreObjects;

import com.google.common.collect.*;
import org.apache.commons.lang3.StringUtils;
import org.hisp.dhis.common.*;
import org.hisp.dhis.commons.collection.CollectionUtils;
import org.hisp.dhis.commons.collection.ListUtils;
import org.hisp.dhis.dataelement.*;
import org.hisp.dhis.dataset.DataSet;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.organisationunit.OrganisationUnitGroupSet;
import org.hisp.dhis.organisationunit.OrganisationUnitLevel;
import org.hisp.dhis.period.Period;
import org.hisp.dhis.period.PeriodType;
import org.hisp.dhis.period.comparator.DescendingPeriodComparator;
import org.hisp.dhis.program.Program;
import org.hisp.dhis.program.ProgramStage;
import org.hisp.dhis.system.util.MathUtils;
import org.hisp.dhis.user.User;
import org.springframework.util.Assert;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

import static org.hisp.dhis.common.DimensionType.*;
import static org.hisp.dhis.common.DimensionalObject.*;
import static org.hisp.dhis.common.DimensionalObjectUtils.asList;
import static org.hisp.dhis.common.DimensionalObjectUtils.getList;

/**
 * Class representing query parameters for retrieving aggregated data from the
 * analytics service. Example instantiation:
 * 
 * <pre>
 * {@code
 * DataQueryParams params = DataQueryParams.newBuilder()
 *      .withDataElements( deA, deB )
 *      .withOrganisationUnits( ouA, ouB )
 *      .withFilterPeriods( peA, peB )
 *      .build();
 * }
 * </pre>
 * 
 * @author Lars Helge Overland
 */
public class DataQueryParams
{
    public static final String VALUE_ID = "value";
    public static final String NUMERATOR_ID = "numerator";
    public static final String DENOMINATOR_ID = "denominator";
    public static final String PERIOD_START_DATE_ID = "pestartdate";
    public static final String PERIOD_END_DATE_ID = "peenddate";
    public static final String FACTOR_ID = "factor";
    public static final String LEVEL_PREFIX = "uidlevel";
    public static final String KEY_DE_GROUP = "DE_GROUP-";
    public static final String KEY_IN_GROUP = "IN_GROUP-";

    public static final String VALUE_HEADER_NAME = "Value";
    public static final String NUMERATOR_HEADER_NAME = "Numerator";
    public static final String DENOMINATOR_HEADER_NAME = "Denominator";
    public static final String PERIOD_START_DATE_NAME = "Period start date";
    public static final String PERIOD_END_DATE_NAME = "Period end date";
    public static final String FACTOR_HEADER_NAME = "Factor";
    
    public static final String DISPLAY_NAME_DATA_X = "Data";
    public static final String DISPLAY_NAME_CATEGORYOPTIONCOMBO = "Category option combo";
    public static final String DISPLAY_NAME_ATTRIBUTEOPTIONCOMBO = "Attribute option combo";
    public static final String DISPLAY_NAME_PERIOD = "Period";
    public static final String DISPLAY_NAME_ORGUNIT = "Organisation unit";
    public static final String DISPLAY_NAME_ORGUNIT_GROUP = "Organisation unit group";
    public static final String DISPLAY_NAME_LONGITUDE = "Longitude";
    public static final String DISPLAY_NAME_LATITUDE = "Latitude";
    
    public static final String PREFIX_ORG_UNIT_LEVEL = "oulevel";

    public static final int DX_INDEX = 0;

    public static final ImmutableSet<Class<? extends IdentifiableObject>> DYNAMIC_DIM_CLASSES = ImmutableSet.of( 
        OrganisationUnitGroupSet.class, DataElementGroupSet.class, CategoryOptionGroupSet.class, DataElementCategory.class );
    
    private static final ImmutableSet<String> DIMENSION_PERMUTATION_IGNORE_DIMS = ImmutableSet.of(
        DATA_X_DIM_ID, CATEGORYOPTIONCOMBO_DIM_ID );
    public static final ImmutableSet<DimensionType> COMPLETENESS_DIMENSION_TYPES = ImmutableSet.of( 
        DATA_X, PERIOD, ORGANISATION_UNIT, ORGANISATION_UNIT_GROUP_SET, CATEGORY_OPTION_GROUP_SET, CATEGORY );
    
    private static final DimensionItem[] DIM_OPT_ARR = new DimensionItem[0];
    private static final DimensionItem[][] DIM_OPT_2D_ARR = new DimensionItem[0][];

    /**
     * The dimensions.
     */
    protected List<DimensionalObject> dimensions = new ArrayList<>();
    
    /**
     * The filters.
     */
    protected List<DimensionalObject> filters = new ArrayList<>();

    /**
     * The aggregation type.
     */
    protected AnalyticsAggregationType aggregationType;

    /**
     * The measure criteria, which is measure filters and corresponding values.
     */
    protected Map<MeasureFilter, Double> measureCriteria = new HashMap<>();

    /**
     * The pre aggregate measure criteria, different to measure criteria, as it is handled in the query itself and not
     * after the query returns.
     */
    protected Map<MeasureFilter, Double> preAggregateMeasureCriteria = new HashMap<>();
    
    /**
     * Indicates if the meta data part of the query response should be omitted.
     */
    protected boolean skipMeta;
    
    /**
     * Indicates if the data part of the query response should be omitted.
     */
    protected boolean skipData;
    
    /**
     * Indicates if the headers of the query response should be omitted.
     */
    protected boolean skipHeaders;

    /**
     * Indicates that full precision should be provided for values.
     */
    protected boolean skipRounding;

    /**
     * Indicates whether to include completed events only.
     */
    protected boolean completedOnly;
    
    /**
     * Indicates i) if the names of all ancestors of the organisation units part
     * of the query should be included in the "names" key and ii) if the hierarchy 
     * path of all organisation units part of the query should be included as a
     * "ouHierarchy" key in the meta-data part of the response.
     */
    protected boolean hierarchyMeta;
    
    /**
     * Indicates whether the maximum number of records to include the response
     * should be ignored.
     */
    protected boolean ignoreLimit;
    
    /**
     * Indicates whether rows with no values should be hidden in the response.
     * Applies to responses with table layout only. 
     */
    protected boolean hideEmptyRows;
    
    /**
     * Indicates whether columns with no values should be hidden in the response.
     * Applies to responses with table layout only. 
     */
    protected boolean hideEmptyColumns;
    
    /**
     * Indicates whether the org unit hierarchy path should be displayed with the
     * org unit names on rows.
     */
    protected boolean showHierarchy;
    
    /**
     * Indicates whether to include the numerator, denominator and factor of 
     * values where relevant in the response.
     */
    protected boolean includeNumDen;
    
    /**
     * Indicates whether to include the start and end dates of the aggregation
     * period in the response.
     */
    protected boolean includePeriodStartEndDates;

    /**
     * Indicates whether to include metadata details to response
     */
    protected boolean includeMetadataDetails;
    
    /**
     * Indicates which property to display for meta-data.
     */
    protected DisplayProperty displayProperty;
    
    /**
     * The scheme to use as identifier in the query response.
     */
    protected IdScheme outputIdScheme;

    /**
     * The output format, default is OutputFormat.ANALYTICS.
     */
    protected OutputFormat outputFormat;
    
    /**
     * Indicates whether to return duplicate data values only. Applicable to
     * {@link OutputFormat} DATA_VALUE_SET only. 
     */
    protected boolean duplicatesOnly;
    
    /**
     * The required approval level identifier for data to be included in query response.
     */
    protected String approvalLevel;
    
    /**
     * The start date for the period dimension, can be null.
     */
    protected Date startDate;

    /**
     * The end date fore the period dimension, can be null.
     */
    protected Date endDate;
    
    /**
     * The order in which the data values has to be sorted, can be null.
     */
    protected SortOrder order;
    
    /**
     * The API version used for the request.
     */
    protected DhisApiVersion apiVersion = DhisApiVersion.DEFAULT;

    // -------------------------------------------------------------------------
    // Event transient properties
    // -------------------------------------------------------------------------
    
    /**
     * The program for events.
     */
    protected transient Program program;
    
    /**
     * The program stage for events.
     */
    protected transient ProgramStage programStage;
    
    // -------------------------------------------------------------------------
    // Transient properties
    // -------------------------------------------------------------------------
    
    /**
     * User to override the current user from the security context.
     */
    protected transient User currentUser;
    
    /**
     * The partitions containing data relevant to this query.
     */
    protected transient Partitions partitions;
    
    /**
     * The name of the analytics table to use for this query.
     */
    protected transient String tableName;

    /**
     * The data type for this query.
     */
    protected transient DataType dataType;
        
    /**
     * The aggregation period type for this query.
     */
    protected transient String periodType;
    
    /**
     * The period type of the data values to query.
     */
    protected transient PeriodType dataPeriodType;
    
    /**
     * Indicates whether to skip partitioning during query planning.
     */
    protected transient boolean skipPartitioning;

    /**
     * Applies to reporting rates only. Indicates whether only timely reports
     * should be returned.
     */
    protected boolean timely;
    
    /**
     * Current organisation unit levels;
     */
    protected List<OrganisationUnitLevel> orgUnitLevels = new ArrayList<>();
    
    /**
     * Applies to reporting rates only. Indicates whether only organisation units
     * which opening or closed date spans the aggregation period should be included
     * as reporting rate targets.
     */
    protected boolean restrictByOrgUnitOpeningClosedDate;
    
    /**
     * Applies to reporting rates only. Indicates whether only organisation units
     * which category options which start or end date spans the aggregation period
     * should be included as reporting rate targets.
     */
    protected boolean restrictByCategoryOptionStartEndDate;
    
    /**
     * Mapping of organisation unit sub-hierarchy roots and lowest available data approval levels.
     */
    protected transient Map<OrganisationUnit, Integer> dataApprovalLevels = new HashMap<>();

    /**
     * Hints for the aggregation process.
     */
    protected transient Set<ProcessingHint> processingHints = new HashSet<>();
    
    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------
    
    protected DataQueryParams()
    {
    }
    
    /**
     * Creates a new Builder for DataQueryParams.
     * 
     * @return a Builder for DataQueryParams.
     */
    public static Builder newBuilder()
    {
        return new DataQueryParams.Builder();
    }
    
    /**
     * Creates a new Builder for DataQueryParams based on the given query. The
     * builder state will be equal to the state of the given query.
     * 
     * @param params the DataQueryParams to use as starting point for this query.
     * @return a Builder for DataQueryParams.
     */
    public static Builder newBuilder( DataQueryParams params )
    {
        return new DataQueryParams.Builder( params );
    }

    protected DataQueryParams instance()
    {
        return copyTo( new DataQueryParams() );
    }
    
    /**
     * Copies all properties of this query onto the given query.
     * <p>
     * The <pre>processingHints</pre> set is not copied.
     * 
     * @param params the query to copy properties onto.
     * @return the given property with all properties of this query set.
     */
    public <T extends DataQueryParams> T copyTo( T params )
    {        
        params.dimensions = DimensionalObjectUtils.getCopies( this.dimensions );
        params.filters = DimensionalObjectUtils.getCopies( this.filters );
        params.aggregationType = this.aggregationType;
        params.measureCriteria = this.measureCriteria;
        params.preAggregateMeasureCriteria = this.preAggregateMeasureCriteria;
        params.startDate = this.startDate;
        params.endDate = this.endDate;
        params.skipMeta = this.skipMeta;
        params.skipData = this.skipData;
        params.skipHeaders = this.skipHeaders;
        params.skipRounding = this.skipRounding;
        params.completedOnly = this.completedOnly;
        params.hierarchyMeta = this.hierarchyMeta;
        params.ignoreLimit = this.ignoreLimit;
        params.hideEmptyRows = this.hideEmptyRows;
        params.showHierarchy = this.showHierarchy;
        params.includeNumDen = this.includeNumDen;
        params.includePeriodStartEndDates = this.includePeriodStartEndDates;
        params.includeMetadataDetails = this.includeMetadataDetails;
        params.displayProperty = this.displayProperty;
        params.outputIdScheme = this.outputIdScheme;
        params.outputFormat = this.outputFormat;
        params.duplicatesOnly = this.duplicatesOnly;
        params.approvalLevel = this.approvalLevel;
        params.startDate = this.startDate;
        params.endDate = this.endDate;
        params.order = this.order;
        params.apiVersion = this.apiVersion;
        
        params.currentUser = this.currentUser;
        params.partitions = new Partitions( this.partitions );
        params.tableName = this.tableName;
        params.dataType = this.dataType;
        params.periodType = this.periodType;
        params.dataPeriodType = this.dataPeriodType;
        params.skipPartitioning = this.skipPartitioning;
        params.timely = this.timely;
        params.orgUnitLevels = this.orgUnitLevels;
        params.restrictByOrgUnitOpeningClosedDate = this.restrictByOrgUnitOpeningClosedDate;
        params.restrictByCategoryOptionStartEndDate = this.restrictByCategoryOptionStartEndDate;
        params.dataApprovalLevels = new HashMap<>( this.dataApprovalLevels );
        
        return params;
    }

    // -------------------------------------------------------------------------
    // Logic read methods
    // -------------------------------------------------------------------------
        
    /**
     * Returns a key representing a group of queries which should be run in 
     * sequence. Currently queries with different aggregation type are run in
     * sequence. It is not allowed for the implementation to differentiate on
     * dimensional objects.
     */
    public String getSequentialQueryGroupKey()
    {
        return aggregationType != null ? aggregationType.toString() : null;
    }
            
    /**
     * Creates a mapping between filter dimension identifiers and filter dimensions. 
     * Filters are guaranteed not to be null.
     */
    public ListMap<String, DimensionalObject> getDimensionFilterMap()
    {
        ListMap<String, DimensionalObject> map = new ListMap<>();
        
        for ( DimensionalObject filter : filters )
        {
            if ( filter != null )
            {
                map.putValue( filter.getDimension(), filter );
            }
        }
        
        return map;
    }
        
    /**
     * Returns the index of the period dimension in the dimension map.
     */
    public int getPeriodDimensionIndex()
    {
        return getDimensionIdentifiersAsList().indexOf( PERIOD_DIM_ID );
    }
    
    /**
     * Returns the dimensions which are part of dimensions and filters. If any
     * such dimensions exist this object is in an illegal state.
     */
    public Collection<DimensionalObject> getDimensionsAsFilters()
    {
        return CollectionUtils.intersection( dimensions, filters );
    }
    
    /**
     * Indicates whether periods are present as a dimension or as a filter. If
     * not this object is in an illegal state.
     */
    public boolean hasPeriods()
    {
        List<DimensionalItemObject> dimOpts = getDimensionOptions( PERIOD_DIM_ID );
        List<DimensionalItemObject> filterOpts = getFilterOptions( PERIOD_DIM_ID );
        
        return !dimOpts.isEmpty() || !filterOpts.isEmpty();
    }

    /**
     * Returns the latest period based on the period end date.
     */
    public Period getLatestPeriod()
    {        
        return getAllPeriods().stream()
            .map( obj -> (Period) obj )
            .min( DescendingPeriodComparator.INSTANCE )
            .orElse( null );
    }
    
    /**
     * Finds the latest endDate associated with this DataQueryParams. Checks endDate, period dimensions and
     * period filters.
     */
    public Date getLatestEndDate()
    {
        Date latestEndDate = new Date( Long.MIN_VALUE );

        if ( endDate != null && endDate.after( latestEndDate ) )
        {
            latestEndDate = endDate;
        }

        for ( DimensionalItemObject object : getAllPeriods() )
        {
            Period period = (Period) object;

            latestEndDate = period.getEndDate().after( latestEndDate ) ? period.getEndDate() : latestEndDate;
        }

        return latestEndDate;
    }
    
    /**
     * Finds the earliest startDate associated with this DataQueryParams. Checks startDate, period dimensions and
     * period filters.
     */
    public Date getEarliestStartDate()
    {
        Date earliestStartDate = new Date( Long.MAX_VALUE );

        if ( startDate != null && startDate.before( earliestStartDate ) )
        {
            earliestStartDate = startDate;
        }
        
        for ( DimensionalItemObject object : getAllPeriods() )
        {
            Period period = (Period) object;

            earliestStartDate = period.getStartDate().before( earliestStartDate ) ? period.getStartDate() : earliestStartDate;
        }

        return earliestStartDate;
    }
    
    /**
     * Indicates whether organisation units are present as dimension or filter.
     */
    public boolean hasOrganisationUnits()
    {
        List<DimensionalItemObject> dimOpts = getDimensionOptions( ORGUNIT_DIM_ID );
        List<DimensionalItemObject> filterOpts = getFilterOptions( ORGUNIT_DIM_ID );
        
        return !dimOpts.isEmpty() || !filterOpts.isEmpty();
    }
    
    /**
     * Returns the period type of the first period specified as filter, or
     * null if there is no period filter.
     */
    public PeriodType getFilterPeriodType()
    {
        List<DimensionalItemObject> filterPeriods = getFilterPeriods();
        
        if ( !filterPeriods.isEmpty() )
        {
            return ( (Period) filterPeriods.get( 0 ) ).getPeriodType();
        }
        
        return null;
    }
    
    /**
     * Returns the first period specified as filter, or null if there is no
     * period filter.
     */
    public Period getFilterPeriod()
    {
        List<DimensionalItemObject> filterPeriods = getFilterPeriods();
        
        if ( !filterPeriods.isEmpty() )
        {
            return (Period) filterPeriods.get( 0 );
        }
        
        return null;
    }
        
    /**
     * Returns a list of dimensions which occur more than once, not including
     * the first duplicate.
     */
    public List<DimensionalObject> getDuplicateDimensions()
    {
        Set<DimensionalObject> dims = new HashSet<>();
        List<DimensionalObject> duplicates = new ArrayList<>();
        
        for ( DimensionalObject dim : dimensions )
        {
            if ( !dims.add( dim ) )
            {
                duplicates.add( dim );
            }
        }
        
        return duplicates;
    }
    
    /**
     * Returns a mapping between identifier and period type for all data sets
     * in this query.
     */
    public Map<String, PeriodType> getDataSetPeriodTypeMap()
    {
        Map<String, PeriodType> map = new HashMap<>();
        
        for ( DimensionalItemObject reportingRate : getReportingRates() )
        {
            ReportingRate rr = (ReportingRate) reportingRate;
            
            DataSet ds = rr.getDataSet();
                        
            map.put( ds.getUid(), ds.getPeriodType() );
        }
        
        return map;
    }
    
    /**
     * Returns the list of organisation unit levels as dimensions.
     */
    public List<DimensionalObject> getOrgUnitLevelsAsDimensions()
    {
        return orgUnitLevels.stream()
            .map( l -> new BaseDimensionalObject( PREFIX_ORG_UNIT_LEVEL + l.getLevel(), 
                DimensionType.ORGANISATION_UNIT_LEVEL, PREFIX_ORG_UNIT_LEVEL + l.getLevel(), l.getName(), Lists.newArrayList() ) )
            .collect( Collectors.toList() );
    }
    
    /**
     * Indicates whether this query is of the given data type.
     */
    public boolean isDataType( DataType dataType )
    {
        return this.dataType != null && this.dataType.equals( dataType );
    }
    
    /**
     * Indicates whether an aggregation type is specified.
     */
    public boolean hasAggregationType()
    {
        return this.aggregationType != null;
    }

    /**
     * Indicates whether the aggregation type is of type disaggregation.
     */
    public boolean isDisaggregation()
    {
        return aggregationType != null && aggregationType.isDisaggregation();
    }

    /**
     * Indicates whether this query requires aggregation of data. No aggregation
     * takes place if aggregation type is none or if data type is text.
     */
    public boolean isAggregation()
    {
        return !( isAggregationType( AggregationType.NONE ) || DataType.TEXT == dataType );
    }
    
    /**
     * Indicates whether this query has the given aggregation type.
     */
    public boolean isAggregationType( AggregationType type )
    {
        return aggregationType != null && aggregationType.isAggregationType( type );
    }
    
    /**
     * Indicates whether the this parameters has the given output format specified.
     */
    public boolean isOutputFormat( OutputFormat format )
    {
        return this.outputFormat != null && this.outputFormat == format;
    }

    /**
     * Creates a mapping between the data periods, based on the data period type
     * for this query, and the aggregation periods for this query.
     */
    public ListMap<DimensionalItemObject, DimensionalItemObject> getDataPeriodAggregationPeriodMap()
    {
        ListMap<DimensionalItemObject, DimensionalItemObject> map = new ListMap<>();

        if ( dataPeriodType != null )
        {
            for ( DimensionalItemObject aggregatePeriod : getDimensionOrFilterItems( PERIOD_DIM_ID ) )
            {
                Period dataPeriod = dataPeriodType.createPeriod( ((Period) aggregatePeriod).getStartDate() );
                
                map.putValue( dataPeriod, aggregatePeriod );
            }
        }
        
        return map;
    }
    
    /**
     * Generates all permutations of the dimension options for this query.
     * Ignores the data and category option combo dimensions.
     */
    public List<List<DimensionItem>> getDimensionItemPermutations()
    {
        List<DimensionItem[]> dimensionOptions = new ArrayList<>();
        
        for ( DimensionalObject dimension : dimensions )
        {
            if ( !DIMENSION_PERMUTATION_IGNORE_DIMS.contains( dimension.getDimension() ) )
            {
                List<DimensionItem> options = new ArrayList<>();
                
                for ( DimensionalItemObject option : dimension.getItems() )
                {
                    options.add( new DimensionItem( dimension.getDimension(), option ) );
                }
                
                dimensionOptions.add( options.toArray( DIM_OPT_ARR ) );
            }
        }
        
        CombinationGenerator<DimensionItem> generator = new CombinationGenerator<>( dimensionOptions.toArray( DIM_OPT_2D_ARR ) );
        
        return generator.getCombinations();
    }

    /**
     * Retrieves the options for all data-related (dx) dimensions and filters.
     * Returns an empty list if not present.
     */
    public List<DimensionalItemObject> getDataDimensionAndFilterOptions()
    {
        List<DimensionalItemObject> options = new ArrayList<>();
        options.addAll( getDimensionOptions( DATA_X_DIM_ID ) );
        options.addAll( getFilterOptions( DATA_X_DIM_ID ) );
        return options;
    }
    
    /**
     * Retrieves the options for the given dimension identifier. Returns an empty
     * list if the dimension is not present.
     */
    public List<DimensionalItemObject> getDimensionOptions( String dimension )
    {
        int index = dimensions.indexOf( new BaseDimensionalObject( dimension ) );

        return index != -1 ? dimensions.get( index ).getItems() : new ArrayList<>( );
    }
    
    /**
     * Retrieves the dimension with the given dimension identifier. Returns null 
     * if the dimension is not present.
     */
    public DimensionalObject getDimension( String dimension )
    {
        int index = dimensions.indexOf( new BaseDimensionalObject( dimension ) );
        
        return index != -1 ? dimensions.get( index ) : null;
    }

    /**
     * Retrieves the dimension or filter with the given dimension identifier. 
     * Returns null if the dimension or filter is not present.
     */
    public DimensionalObject getDimensionOrFilter( String dimension )
    {
        DimensionalObject dim = getDimension( dimension );
        
        return dim != null ? dim : getFilter( dimension );
    }
    
    /**
     * Retrieves the options for the given filter. Returns an empty list if the
     * filter is not present.
     */
    public List<DimensionalItemObject> getFilterOptions( String filter )
    {
        int index = filters.indexOf( new BaseDimensionalObject( filter ) );
        
        return index != -1 ? filters.get( index ).getItems() : new ArrayList<DimensionalItemObject>();
    }

    /**
     * Retrieves the filter with the given filter identifier.
     */
    public DimensionalObject getFilter( String filter )
    {
        int index = filters.indexOf( new BaseDimensionalObject( filter ) );
        
        return index != -1 ? filters.get( index ) : null;
    }
    
    /**
     * Get all filter items.
     */
    public List<DimensionalItemObject> getFilterItems()
    {
        List<DimensionalItemObject> filterItems = new ArrayList<>();
        
        for ( DimensionalObject filter : filters )
        {
            if ( filter != null && filter.hasItems() )
            {
                filterItems.addAll( filter.getItems() );
            }
        }
        
        return filterItems;
    }
    
    /**
     * Returns a list of dimensions and filters in the mentioned, preserved order.
     */
    public List<DimensionalObject> getDimensionsAndFilters()
    {
        List<DimensionalObject> list = new ArrayList<>();
        list.addAll( dimensions );
        list.addAll( filters );
        return list;
    }
    
    /**
     * Returns a list of dimensions and filters of the given dimension type.
     */
    public List<DimensionalObject> getDimensionsAndFilters( DimensionType dimensionType )
    {
        List<DimensionalObject> list = new ArrayList<>();
        
        if ( dimensionType != null )
        {
            for ( DimensionalObject dimension : getDimensionsAndFilters() )
            {
                if ( dimension.getDimensionType().equals( dimensionType ) )
                {
                    list.add( dimension );
                }
            }
        }
        
        return list;
    }

    /**
     * Returns a list of dimensions and filters of the given set of dimension types.
     */
    public List<DimensionalObject> getDimensionsAndFilters( Set<DimensionType> dimensionTypes )
    {
        List<DimensionalObject> list = new ArrayList<>();
        
        for ( DimensionalObject dimension : getDimensionsAndFilters() )
        {
            if ( dimensionTypes.contains( dimension.getDimensionType() ) )
            {
                list.add( dimension );
            }
        }
        
        return list;
    }
    
    /**
     * Indicates whether all dimensions and filters have value types among the given
     * set of value types.
     */
    public boolean containsOnlyDimensionsAndFilters( Set<DimensionType> dimensionTypes )
    {
        for ( DimensionalObject dimension : getDimensionsAndFilters() )
        {
            if ( !dimensionTypes.contains( dimension.getDimensionType() ) )
            {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Retrieves the options for the the dimension or filter with the given 
     * identifier. Returns an empty list if the dimension or filter is not present.
     */
    public List<DimensionalItemObject> getDimensionOrFilterItems( String key )
    {
        List<DimensionalItemObject> dimensionOptions = getDimensionOptions( key );

        return !dimensionOptions.isEmpty() ? dimensionOptions : getFilterOptions( key );
    }

    private List<DimensionalItemObject> getDimensionItemObjects( String dimension )
    {
        List<DimensionalItemObject> items = new ArrayList<>();

        if ( CATEGORYOPTIONCOMBO_DIM_ID.equals( dimension ) )
        {
            List<DimensionalItemObject> des = getDataElements();

            if ( !des.isEmpty() )
            {
                Set<DataElementCategoryCombo> categoryCombos = Sets.newHashSet();

                for ( DimensionalItemObject de : des )
                {
                    categoryCombos.addAll( ((DataElement) de).getCategoryCombos() );
                }

                for ( DataElementCategoryCombo cc : categoryCombos )
                {
                    items.addAll( cc.getSortedOptionCombos() );
                }
            }
        }
        else
        {
            items.addAll( getDimensionOptions( dimension ) );
        }

        return items;
    }

    /**
     * Retrieves the options for the given dimension identifier. If the "co"
     * dimension is specified, all category option combinations for the first data
     * element is returned. Returns an empty array if the dimension is not present.
     */
    public DimensionalItemObject[] getDimensionItemArrayExplodeCoc( String dimension )
    {
        return getDimensionItemObjects( dimension ).toArray( new DimensionalItemObject[0] );
    }

    public List<EventAnalyticsDimensionalItem> getEventReportDimensionalItemArrayExploded( String dimension )
    {
        return getDimensionItemObjects( dimension ).stream()
            .map( item -> new EventAnalyticsDimensionalItem( item, dimension ) ).collect( Collectors.toList() );
    }
    
    /**
     * Indicates whether a dimension or filter with the given dimension / filter
     * identifier exists.
     */
    public boolean hasDimensionOrFilter( String key )
    {
        return dimensions.indexOf( new BaseDimensionalObject( key ) ) != -1 || filters.indexOf( new BaseDimensionalObject( key ) ) != -1;
    }

    /**
     * Indicates whether a dimension or filter which specifies dimension items 
     * with the given identifier exists.
     */
    public boolean hasDimensionOrFilterWithItems( String key )
    {
        return !getDimensionOrFilterItems( key ).isEmpty();
    }

    /**
     * Indicates whether a dimension with the given identifier exists.
     */
    public boolean hasDimension( String key )
    {
        return dimensions.indexOf( new BaseDimensionalObject( key ) ) != -1;
    }
    
    /**
     * Indicates whether a filter with the given identifier exists.
     */
    public boolean hasFilter( String key )
    {
        return filters.indexOf( new BaseDimensionalObject( key ) ) != -1;
    }
    
    /**
     * Retrieves the set of dimension types which are present in dimensions and
     * filters.
     */
    public Set<DimensionType> getDimensionTypes()
    {
        Set<DimensionType> types = new HashSet<>();
        
        for ( DimensionalObject dim : getDimensionsAndFilters() )
        {
            types.add( dim.getDimensionType() );
        }
        
        return types;
    }
    
    /**
     * Returns the number of days to use as denominator when aggregating
     * "average sum in hierarchy" aggregate values. If period is dimension,
     * use the number of days in the first period. In these cases, queries
     * should contain periods with the same number of days only. If period
     * is filter, use the sum of days in all periods.
     */
    public int getDaysForAvgSumIntAggregation()
    {        
        if ( hasDimension( PERIOD_DIM_ID ) )
        {
            List<DimensionalItemObject> periods = getPeriods();

            Assert.isTrue( !periods.isEmpty(), "At least one period must exist" );
            
            Period period = (Period) periods.get( 0 );
            
            return period.getDaysInPeriod();
        }
        else
        {
            List<DimensionalItemObject> periods = getFilterPeriods();
            
            int totalDays = 0;
            
            for ( DimensionalItemObject item : periods )
            {
                Period period = (Period) item;
                
                totalDays += period.getDaysInPeriod();
            }
            
            return totalDays;
        }
    }
    
    /**
     * Indicates whether this query defines an identifier scheme different from
     * UID.
     */
    public boolean hasNonUidOutputIdScheme()
    {
        return outputIdScheme != null && !IdScheme.UID.equals( outputIdScheme );
    }

    /**
     * Indicates whether this query specifies data approval levels.
     */
    public boolean isDataApproval()
    {
        return dataApprovalLevels != null && !dataApprovalLevels.isEmpty();
    }
    
    /**
     * Indicates whether this query specifies a approval level.
     */
    public boolean hasApprovalLevel()
    {
        return approvalLevel != null;
    }

    /**
     * Returns all dimension items.
     */
    public List<DimensionalItemObject> getAllDimensionItems()
    {
        List<DimensionalItemObject> items = new ArrayList<>();
        
        for ( DimensionalObject dim : ListUtils.union( dimensions, filters ) )
        {
            items.addAll( dim.getItems() );
        }
        
        return items;
    }
    
    /**
     * Indicates whether this query has any partitions.
     */
    public boolean hasPartitions()
    {
        return partitions != null && partitions.hasAny();
    }
    
    /**
     * Indicates whether this query has a data period type.
     */
    public boolean hasDataPeriodType()
    {
        return dataPeriodType != null;
    }

    /**
     * Indicates whether this query has a start and end date.
     */
    public boolean hasStartEndDate()
    {
        return startDate != null && endDate != null;
    }

    /**
     * Indicates whether this query requires ordering of data values.
     * 
     * @return true if ordering is required , false otherwise.
     */
    public boolean hasOrder()
    {
    		return order != null;
    }
    
    /**
     * Indicates whether this object has a program.
     */
    public boolean hasProgram()
    {
        return program != null;
    }

    /**
     * Indicates whether this query has a program stage.
     */
    public boolean hasProgramStage()
    {
        return programStage != null;
    }

    /**
     * Indicates whether this query has any measure criteria defined.
     */
    public boolean hasMeasureCriteria()
    {
        return measureCriteria != null && !measureCriteria.isEmpty();
    }
    
    /**
     * Indicates whether this query has any pre-aggregate measure criteria defined.
     */
    public boolean hasPreAggregateMeasureCriteria()
    {
        return preAggregateMeasureCriteria != null && !preAggregateMeasureCriteria.isEmpty();
    }
    
    /**
     * Indicates whether the given processing hint exists.
     */
    public boolean hasProcessingHint( ProcessingHint hint )
    {
        return this.processingHints.contains( hint );
    }
    
    /**
     * Indicates whether this query has a single indicator specified as dimension
     * option for the data dimension.
     */
    public boolean hasSingleIndicatorAsDataFilter()
    {
        return getFilterIndicators().size() == 1 && getFilterOptions( DATA_X_DIM_ID ).size() == 1;
    }

    /**
     * Indicates whether this query has a single reporting rate specified as dimension
     * option for the data dimension.
     */
    public boolean hasSingleReportingRateAsDataFilter()
    {
        return getFilterReportingRates().size() == 1 && getFilterOptions( DATA_X_DIM_ID ).size() == 1;
    }
        
    /**
     * Indicates whether this query has a current user specified.
     */
    public boolean hasCurrentUser()
    {
        return currentUser != null;
    }
    
    // -------------------------------------------------------------------------
    // Supportive protected methods
    // -------------------------------------------------------------------------

    /**
     * Removes the dimension or filter with the given identifier.
     */
    protected DataQueryParams removeDimensionOrFilter( String dimension )
    {
        removeDimension( dimension );
        removeFilter( dimension );
        
        return this;
    }

    /**
     * Sets the given options for the given dimension. If the dimension exists, 
     * replaces the dimension items with the given items. If not, creates a new 
     * dimension with the given items.
     */
    protected DataQueryParams setDimensionOptions( String dimension, DimensionType type, String dimensionName, List<DimensionalItemObject> options )
    {
        int index = dimensions.indexOf( new BaseDimensionalObject( dimension ) );
        
        if ( index != -1 )
        {
            dimensions.set( index, new BaseDimensionalObject( dimension, type, dimensionName, null, options ) );
        }
        else
        {
            dimensions.add( new BaseDimensionalObject( dimension, type, dimensionName, null, options ) );
        }
        
        return this;
    }
    
    /**
     * Adds the given dimension to the dimensions of this query. The dimensions will 
     * be ordered according to the order property value of the {@link DimensionType}
     * of the dimension.
     */
    protected void addDimension( DimensionalObject dimension )
    {
        dimensions.add( dimension );
        
        Collections.sort( dimensions, ( o1, o2 ) -> o1.getDimensionType().getOrder() - o2.getDimensionType().getOrder() );
    }
    
    /**
     * Adds the given filter to the filters of this query.
     */
    protected void addFilter( DimensionalObject filter )
    {
        filters.add( filter );
    }

    // -------------------------------------------------------------------------
    // Supportive private methods
    // -------------------------------------------------------------------------

    /**
     * Replaces the periods of this query with the corresponding data periods.
     * Sets the period type to the data period type. This method is relevant only 
     * when then the data period type has lower frequency than the aggregation 
     * period type. This is valid because disaggregation is allowed for data
     * with average aggregation operator.
     */
    private void replaceAggregationPeriodsWithDataPeriods( ListMap<DimensionalItemObject, DimensionalItemObject> dataPeriodAggregationPeriodMap )
    {        
        this.periodType = this.dataPeriodType.getName();
        
        if ( !getPeriods().isEmpty() ) // Period is dimension
        {
            setDimensionOptions( PERIOD_DIM_ID, DimensionType.PERIOD, dataPeriodType.getName().toLowerCase(), new ArrayList<>( dataPeriodAggregationPeriodMap.keySet() ) );
        }
        else // Period is filter
        {
            setFilterOptions( PERIOD_DIM_ID, DimensionType.PERIOD, dataPeriodType.getName().toLowerCase(), new ArrayList<>( dataPeriodAggregationPeriodMap.keySet() ) );
        }
    }
    
    /**
     * Sets the {@code startDate} property to the earliest start date, and the
     * {@code endDate} property to the latest end date based on periods.
     */
    private void setEarliestStartDateLatestEndDate()
    {
        this.startDate = getEarliestStartDate();
        this.endDate = getLatestEndDate();
    }
    
    /**
     * Adds a period dimension or updates an existing one with no period items. Removes
     * period filter if present.
     */
    private void setPeriodDimensionWithoutOptions()
    {
        removeDimension( PERIOD_DIM_ID );
        setDimensionOptions( PERIOD_DIM_ID, DimensionType.PERIOD, PERIOD_DIM_ID, Lists.newArrayList() );
    }
        
    /**
     * Removes the dimension with the given identifier.
     */
    private DataQueryParams removeDimension( String dimension )
    {
        this.dimensions.remove( new BaseDimensionalObject( dimension ) );
        
        return this;
    }

    /**
     * Removes the filter with the given identifier.
     */
    private DataQueryParams removeFilter( String filter )
    {
        this.filters.remove( new BaseDimensionalObject( filter ) );
        
        return this;
    }

    /**
     * Sets the items for the given dimension, if the dimension exists.
     */
    private DataQueryParams setDimensionOptions( String dimension, List<DimensionalItemObject> options )
    {
        BaseDimensionalObject dim = (BaseDimensionalObject) getDimension( dimension );
        
        if ( dim != null )
        {
            dim.setItems( options );
        }
        
        return this;
    }
    
    /**
     * Sets the options for the given filter.
     */
    private DataQueryParams setFilterOptions( String filter, DimensionType type, String dimensionName, List<DimensionalItemObject> options )
    {
        int index = filters.indexOf( new BaseDimensionalObject( filter ) );
        
        if ( index != -1 )
        {
            filters.set( index, new BaseDimensionalObject( filter, type, dimensionName, null, options ) );
        }
        else
        {
            filters.add( new BaseDimensionalObject( filter, type, dimensionName, null, options ) );
        }
        
        return this;
    }
    
    /**
     * Removes all dimensions which are not of the given type from dimensions
     * and filters.
     */
    private DataQueryParams pruneToDimensionType( DimensionType type )
    {
        Iterator<DimensionalObject> dimensionIter = dimensions.iterator();

        while ( dimensionIter.hasNext() )
        {
            if ( !dimensionIter.next().getDimensionType().equals( type ) )
            {
                dimensionIter.remove();
            }
        }
        
        Iterator<DimensionalObject> filterIter = filters.iterator();
        
        while ( filterIter.hasNext() )
        {
            if ( !filterIter.next().getDimensionType().equals( type ) )
            {
                filterIter.remove();
            }
        }
        
        return this;
    }    

    /**
     * Adds the given dimensions to the dimensions of this query. If the dimension
     * is a data dimension it will be added to the beginning of the list of dimensions.
     */
    private void addDimensions( List<DimensionalObject> dimensions )
    {
        for ( DimensionalObject dim : dimensions )
        {
            addDimension( dim );
        }
    }

    /**
     * Adds the given filters to the filters of this query.
     */
    private void addFilters( List<DimensionalObject> filters )
    {
        for ( DimensionalObject filter : filters )
        {
            addFilter( filter );
        }
    }
    
    /**
     * Returns a list of dimension identifiers for all dimensions.
     */
    private List<String> getDimensionIdentifiersAsList()
    {
        List<String> list = new ArrayList<>();
        
        for ( DimensionalObject dimension : dimensions )
        {
            list.add( dimension.getDimension() );
        }
        
        return list;
    }

    /**
     * Retains only dimensions of the given data dimension item type.
     */
    private DataQueryParams retainDataDimension( DataDimensionItemType itemType )
    {
        DimensionalObject dimension = getDimensionOrFilter( DATA_X_DIM_ID );
        
        List<DimensionalItemObject> items = AnalyticsUtils.getByDataDimensionItemType( itemType, dimension.getItems() );
        
        dimension.getItems().clear();
        dimension.getItems().addAll( items );
        
        return this;
    }
    
    /**
     * Retains only dimensions of type reporting rates and the given reporting
     * rate metric.
     * 
     * @param metric the reporting rate metric.
     */
    private DataQueryParams retainDataDimensionReportingRates( ReportingRateMetric metric )
    {
        DimensionalObject dimension = getDimensionOrFilter( DATA_X_DIM_ID );
        
        List<ReportingRate> items = DimensionalObjectUtils.asTypedList( 
            AnalyticsUtils.getByDataDimensionItemType( DataDimensionItemType.REPORTING_RATE, dimension.getItems() ) );
        
        items = items.stream().filter( r -> metric == r.getMetric() ).collect( Collectors.toList() );
        
        dimension.getItems().clear();
        dimension.getItems().addAll( items );
        
        return this;
    }
    
    /**
     * Retains only dimensions of the given data dimension item types.
     * 
     * @param itemTypes the array of data dimension item types.
     */
    private DataQueryParams retainDataDimensions( DataDimensionItemType... itemTypes )
    {
        DimensionalObject dimension = getDimensionOrFilter( DATA_X_DIM_ID );
        
        List<DimensionalItemObject> items = new ArrayList<>();
        
        for ( DataDimensionItemType itemType : itemTypes )
        {
            items.addAll( AnalyticsUtils.getByDataDimensionItemType( itemType, dimension.getItems() ) );
        }

        dimension.getItems().clear();
        dimension.getItems().addAll( items );
        
        return this;
    }

    /**
     * Sets the given list of data dimension options. Replaces existing options
     * of the given data dimension type.
     * 
     * @param itemType the data dimension type, or all types if null.
     * @param options the data dimension options.
     */
    private void setDataDimensionOptions( @Nullable DataDimensionItemType itemType, List<? extends DimensionalItemObject> options )
    {
        List<DimensionalItemObject> existing = getDimensionOptions( DATA_X_DIM_ID );
        
        if ( itemType != null )
        {
            existing = AnalyticsUtils.getByDataDimensionItemType( itemType, existing );
        }
        
        DimensionalObject dimension = getDimension( DATA_X_DIM_ID );
        
        if ( dimension == null )
        {
            dimension = new BaseDimensionalObject( DATA_X_DIM_ID, DimensionType.DATA_X, null, DISPLAY_NAME_DATA_X, options );
            addDimension( dimension );
        }
        else
        {        
            dimension.getItems().removeAll( existing );
            dimension.getItems().addAll( options );
        }
    }
    
    // -------------------------------------------------------------------------
    // Static methods
    // -------------------------------------------------------------------------

    /**
     * Creates a mapping of permutation keys and mappings of data element operands
     * and values based on the given mapping of dimension option keys and 
     * aggregated values. The data element dimension will be at index 0.
     * 
     * @param aggregatedDataMap the aggregated data map.
     * @return a mapping of permutation keys and mappings of data element operands
     *         and values.
     */
    public static MapMap<String, DimensionalItemObject, Double> getPermutationDimensionalItemValueMap( Map<String, Double> aggregatedDataMap )
    {
        MapMap<String, DimensionalItemObject, Double> permutationMap = new MapMap<>();
        
        for ( String key : aggregatedDataMap.keySet() )
        {
            List<String> keys = Lists.newArrayList( key.split( DIMENSION_SEP ) );
            
            String dimItem = keys.get( DX_INDEX );
                        
            keys.remove( DX_INDEX );
            
            BaseDimensionalItemObject dimItemObject = new BaseDimensionalItemObject( dimItem );
            
            String permKey = StringUtils.join( keys, DIMENSION_SEP );
            
            Double value = aggregatedDataMap.get( key );
            
            permutationMap.putEntry( permKey, dimItemObject, value );            
        }
        
        return permutationMap;
    }
    
    /**
     * Returns a mapping of permutations keys (org unit id or null) and mappings
     * of org unit group and counts, based on the given mapping of dimension option
     * keys and counts.
     */
    public static Map<String, Map<String, Integer>> getPermutationOrgUnitGroupCountMap( Map<String, Double> orgUnitCountMap )
    {
        MapMap<String, String, Integer> countMap = new MapMap<>();
        
        for ( String key : orgUnitCountMap.keySet() )
        {
            List<String> keys = Lists.newArrayList( key.split( DIMENSION_SEP ) );
            
            // Org unit group always at last index, org unit potentially at first
            
            int ougInx = keys.size() - 1;
            
            String oug = keys.get( ougInx );
            
            ListUtils.removeAll( keys, ougInx );

            String permKey = StringUtils.trimToNull( StringUtils.join( keys, DIMENSION_SEP ) );
            
            Integer count = orgUnitCountMap.get( key ).intValue();
            
            countMap.putEntry( permKey, oug, count );
        }
        
        return countMap;
    }

    /**
     * Retrieves the measure criteria from the given string. Criteria are separated
     * by the option separator, while the criterion filter and value are separated
     * with the dimension name separator.
     */
    public static Map<MeasureFilter, Double> getMeasureCriteriaFromParam( String param )
    {
        if ( param == null )
        {
            return null;
        }

        Map<MeasureFilter, Double> map = new HashMap<>();

        String[] criteria = param.split( DimensionalObject.OPTION_SEP );

        for ( String c : criteria )
        {
            String[] criterion = c.split( DimensionalObject.DIMENSION_NAME_SEP );

            if ( criterion != null && criterion.length == 2 && MathUtils.isNumeric( criterion[1] ) )
            {
                MeasureFilter filter = MeasureFilter.valueOf( criterion[0] );
                Double value = Double.valueOf( criterion[1] );
                map.put( filter, value );
            }
        }

        return map;
    }


    // -------------------------------------------------------------------------
    // hashCode, equals and toString
    // -------------------------------------------------------------------------

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( dimensions == null ) ? 0 : dimensions.hashCode() );
        result = prime * result + ( ( filters == null ) ? 0 : filters.hashCode() );
        return result;
    }

    @Override
    public boolean equals( Object object )
    {
        if ( this == object )
        {
            return true;
        }
        
        if ( object == null )
        {
            return false;
        }
        
        if ( getClass() != object.getClass() )
        {
            return false;
        }
        
        DataQueryParams other = (DataQueryParams) object;
        
        if ( dimensions == null )
        {
            if ( other.dimensions != null )
            {
                return false;
            }
        }
        else if ( !dimensions.equals( other.dimensions ) )
        {
            return false;
        }
        
        if ( filters == null )
        {
            if ( other.filters != null )
            {
                return false;
            }
        }
        else if ( !filters.equals( other.filters ) )
        {
            return false;
        }
        
        return true;
    }

    @Override
    public String toString()
    {
        return MoreObjects.toStringHelper( this )
            .add( "Dimensions", dimensions )
            .add( "Filters", filters )
            .add( "Aggregation type", aggregationType )
            .add( "Measure criteria", measureCriteria )
            .add( "Output format", outputFormat )
            .add( "API version", apiVersion )
            .toString();
    }
    
    // -------------------------------------------------------------------------
    // Get and set methods for serialized properties
    // -------------------------------------------------------------------------

    public List<DimensionalObject> getDimensions()
    {
        return ImmutableList.copyOf( dimensions );
    }

    public List<DimensionalObject> getFilters()
    {
        return ImmutableList.copyOf( filters );
    }

    public AnalyticsAggregationType getAggregationType()
    {
        return aggregationType;
    }

    public Map<MeasureFilter, Double> getMeasureCriteria()
    {
        return measureCriteria;
    }

    public Map<MeasureFilter, Double> getPreAggregateMeasureCriteria()
    {
        return preAggregateMeasureCriteria;
    }

    public boolean isSkipMeta()
    {
        return skipMeta;
    }

    public boolean isSkipData()
    {
        return skipData;
    }

    public boolean isSkipHeaders()
    {
        return skipHeaders;
    }

    public boolean isSkipRounding()
    {
        return skipRounding;
    }

    public boolean isCompletedOnly()
    {
        return completedOnly;
    }
    
    public boolean isHierarchyMeta()
    {
        return hierarchyMeta;
    }

    public boolean isIgnoreLimit()
    {
        return ignoreLimit;
    }

    public boolean isHideEmptyRows()
    {
        return hideEmptyRows;
    }

    public boolean isHideEmptyColumns()
    {
        return hideEmptyColumns;
    }

    public boolean isShowHierarchy()
    {
        return showHierarchy;
    }
    
    public boolean isIncludeNumDen()
    {
        return includeNumDen;
    }

    public boolean isIncludePeriodStartEndDates()
    {
        return includePeriodStartEndDates;
    }
    
    public boolean isIncludeMetadataDetails()
    {
        return includeMetadataDetails;
    }

    public DisplayProperty getDisplayProperty()
    {
        return displayProperty;
    }

    public IdScheme getOutputIdScheme()
    {
        return outputIdScheme;
    }

    public OutputFormat getOutputFormat()
    {
        return outputFormat;
    }
    
    public boolean isDuplicatesOnly()
    {
        return duplicatesOnly;
    }

    public String getApprovalLevel()
    {
        return approvalLevel;
    }

    public Date getStartDate()
    {
        return startDate;
    }

    public Date getEndDate()
    {
        return endDate;
    }
    
    public SortOrder getOrder()
    {
    		return order;
    }

    public DhisApiVersion getApiVersion()
    {
        return apiVersion;
    }
    
    public Program getProgram()
    {
        return program;
    }

    public ProgramStage getProgramStage()
    {
        return programStage;
    }

    public void setOutputIdScheme( IdScheme outputIdScheme )
    {
        this.outputIdScheme = outputIdScheme;
    }

    // -------------------------------------------------------------------------
    // Get and set methods for transient properties
    // -------------------------------------------------------------------------

    public User getCurrentUser()
    {
        return currentUser;
    }

    public Partitions getPartitions()
    {
        return partitions;
    }

    public void setPartitions( Partitions partitions )
    {
        this.partitions = partitions;
    }
    
    public String getTableName()
    {
        return tableName;
    }

    public DataType getDataType()
    {
        return dataType;
    }

    public String getPeriodType()
    {
        return periodType;
    }

    public PeriodType getDataPeriodType()
    {
        return dataPeriodType;
    }

    public boolean isSkipPartitioning()
    {
        return skipPartitioning;
    }

    public boolean isTimely()
    {
        return timely;
    }

    public List<OrganisationUnitLevel> getOrgUnitLevels()
    {
        return orgUnitLevels;
    }

    public boolean isRestrictByOrgUnitOpeningClosedDate()
    {
        return restrictByOrgUnitOpeningClosedDate;
    }

    public boolean isRestrictByCategoryOptionStartEndDate()
    {
        return restrictByCategoryOptionStartEndDate;
    }

    public Map<OrganisationUnit, Integer> getDataApprovalLevels()
    {
        return dataApprovalLevels;
    }

    public void setDataApprovalLevels( Map<OrganisationUnit, Integer> dataApprovalLevels )
    {
        this.dataApprovalLevels = dataApprovalLevels;
    }

    // -------------------------------------------------------------------------
    // Get helpers for dimensions and filters
    // -------------------------------------------------------------------------

    /**
     * Returns all data dimension items part of a dimension or filter.
     */
    public List<DimensionalItemObject> getAllDataDimensionItems()
    {
        return ImmutableList.copyOf( ListUtils.union( getDimensionOptions( DATA_X_DIM_ID ), getFilterOptions( DATA_X_DIM_ID ) ) );
    }
    
    /**
     * Returns all indicators part of a dimension or filter.
     */
    public List<DimensionalItemObject> getAllIndicatfors()
    {
        return ImmutableList.copyOf( ListUtils.union( getIndicators(), getFilterIndicators() ) );
    }

    /**
     * Returns all data elements part of a dimension or filter.
     */
    public List<DimensionalItemObject> getAllDataElements()
    {
        return ImmutableList.copyOf( ListUtils.union( getDataElements(), getFilterDataElements() ) );
    }

    /**
     * Returns all reporting rates part of a dimension or filter.
     */
    public List<DimensionalItemObject> getAllReportingRates()
    {
        return ImmutableList.copyOf( ListUtils.union( getReportingRates(), getFilterReportingRates() ) );
    }

    /**
     * Returns all program attributes part of a dimension or filter.
     */
    public List<DimensionalItemObject> getAllProgramAttributes()
    {
        return ImmutableList.copyOf( ListUtils.union( getProgramAttributes(), getFilterProgramAttributes() ) );
    }

    /**
     * Returns all program data elements part of a dimension or filter.
     */
    public List<DimensionalItemObject> getAllProgramDataElements()
    {
        return ImmutableList.copyOf( ListUtils.union( getProgramDataElements(), getFilterProgramDataElements() ) );
    }

    /**
     * Returns all program attributes part of a dimension or filter.
     */
    public List<DimensionalItemObject> getAllProgramDataElementsAndAttributes()
    {
        return ListUtils.union( getAllProgramAttributes(), getAllProgramDataElements() );
    }

    /**
     * Returns all validation results part of a dimension or filter.
     */
    public List<DimensionalItemObject> getAllValidationResults()
    {
        return ImmutableList.copyOf( ListUtils.union( getValidationResults(), getFilterValidationResults() ) );
    }
    
    /**
     * Returns all periods part of a dimension or filter.
     */
    public List<DimensionalItemObject> getAllPeriods()
    {
        return ImmutableList.copyOf( ListUtils.union( getPeriods(), getFilterPeriods() ) );
    }

    // -------------------------------------------------------------------------
    // Get helpers for dimensions
    // -------------------------------------------------------------------------
    
    /**
     * Returns all indicators part of the data dimension.
     */
    public List<DimensionalItemObject> getIndicators()
    {
        return ImmutableList.copyOf( AnalyticsUtils.getByDataDimensionItemType( DataDimensionItemType.INDICATOR, getDimensionOptions( DATA_X_DIM_ID ) ) );
    }

    /**
     * Returns all data elements part of the data dimension.
     */
    public List<DimensionalItemObject> getDataElements()
    {
        return ImmutableList.copyOf( AnalyticsUtils.getByDataDimensionItemType( DataDimensionItemType.DATA_ELEMENT, getDimensionOptions( DATA_X_DIM_ID ) ) );
    }

    /**
     * Returns all data element operands part of the data dimension.
     */
    public List<DimensionalItemObject> getDataElementOperands()
    {
        return ImmutableList.copyOf( AnalyticsUtils.getByDataDimensionItemType( DataDimensionItemType.DATA_ELEMENT_OPERAND, getDimensionOptions( DATA_X_DIM_ID ) ) );
    }

    /**
     * Returns all reporting rates part of the data dimension.
     */
    public List<DimensionalItemObject> getReportingRates()
    {
        return ImmutableList.copyOf( AnalyticsUtils.getByDataDimensionItemType( DataDimensionItemType.REPORTING_RATE, getDimensionOptions( DATA_X_DIM_ID ) ) );
    }

    /**
     * Returns all program indicators part of the data dimension.
     */
    public List<DimensionalItemObject> getProgramIndicators()
    {
        return ImmutableList.copyOf( AnalyticsUtils.getByDataDimensionItemType( DataDimensionItemType.PROGRAM_INDICATOR, getDimensionOptions( DATA_X_DIM_ID ) ) );
    }

    /**
     * Returns all program data elements part of the data dimension.
     */
    public List<DimensionalItemObject> getProgramDataElements()
    {
        return ImmutableList.copyOf( AnalyticsUtils.getByDataDimensionItemType( DataDimensionItemType.PROGRAM_DATA_ELEMENT, getDimensionOptions( DATA_X_DIM_ID ) ) );
    }

    /**
     * Returns all indicators part of the data dimension.
     */
    public List<DimensionalItemObject> getProgramAttributes()
    {
        return ImmutableList.copyOf( AnalyticsUtils.getByDataDimensionItemType( DataDimensionItemType.PROGRAM_ATTRIBUTE, getDimensionOptions( DATA_X_DIM_ID ) ) );
    }

    /**
     * Returns all periods part of the period dimension.
     */
    public List<DimensionalItemObject> getPeriods()
    {
        return ImmutableList.copyOf( getDimensionOptions( PERIOD_DIM_ID ) );
    }

    /**
     * Returns all organisation units part of the organisation unit dimension.
     */
    public List<DimensionalItemObject> getOrganisationUnits()
    {
        return ImmutableList.copyOf( getDimensionOptions( ORGUNIT_DIM_ID ) );
    }

    /**
     * Returns all data element group sets specified as dimensions.
     */
    public List<DimensionalObject> getDataElementGroupSets()
    {
        return ListUtils.union( dimensions, filters ).stream().
            filter( d -> DimensionType.DATA_ELEMENT_GROUP_SET.equals( d.getDimensionType() ) ).collect( Collectors.toList() );
    }

    /**
     * Returns all program data elements part of the data dimension.
     */
    public List<DimensionalItemObject> getValidationResults()
    {
        return ImmutableList.copyOf( AnalyticsUtils.getByDataDimensionItemType( DataDimensionItemType.VALIDATION_RULE, getDimensionOptions( DATA_X_DIM_ID ) ) );
    }
            
    // -------------------------------------------------------------------------
    // Get helpers for filters
    // -------------------------------------------------------------------------
    
    /**
     * Returns all indicators part of the data filter.
     */
    public List<DimensionalItemObject> getFilterIndicators()
    {
        return ImmutableList.copyOf( AnalyticsUtils.getByDataDimensionItemType( DataDimensionItemType.INDICATOR, getFilterOptions( DATA_X_DIM_ID ) ) );
    }

    /**
     * Returns all data elements part of the data filter.
     */
    public List<DimensionalItemObject> getFilterDataElements()
    {
        return ImmutableList.copyOf( AnalyticsUtils.getByDataDimensionItemType( DataDimensionItemType.DATA_ELEMENT, getFilterOptions( DATA_X_DIM_ID ) ) );
    }

    /**
     * Returns all reporting rates part of the data filter.
     */
    public List<DimensionalItemObject> getFilterReportingRates()
    {
        return ImmutableList.copyOf( AnalyticsUtils.getByDataDimensionItemType( DataDimensionItemType.REPORTING_RATE, getFilterOptions( DATA_X_DIM_ID ) ) );
    }

    /**
     * Returns all program data elements part of the data filter.
     */
    public List<DimensionalItemObject> getFilterProgramDataElements()
    {
        return ImmutableList.copyOf( AnalyticsUtils.getByDataDimensionItemType( DataDimensionItemType.PROGRAM_DATA_ELEMENT, getFilterOptions( DATA_X_DIM_ID ) ) );
    }

    /**
     * Returns all program attributes part of the data filter.
     */
    public List<DimensionalItemObject> getFilterProgramAttributes()
    {
        return ImmutableList.copyOf( AnalyticsUtils.getByDataDimensionItemType( DataDimensionItemType.PROGRAM_ATTRIBUTE, getFilterOptions( DATA_X_DIM_ID ) ) );
    }

    /**
     * Returns all validation results part of the validation result filter.
     */
    public List<DimensionalItemObject> getFilterValidationResults()
    {
        return ImmutableList.copyOf( AnalyticsUtils.getByDataDimensionItemType( DataDimensionItemType.VALIDATION_RULE, getFilterOptions( DATA_X_DIM_ID ) ) );
    }

    /**
     * Returns all periods part of the period filter.
     */
    public List<DimensionalItemObject> getFilterPeriods()
    {
        return ImmutableList.copyOf( getFilterOptions( PERIOD_DIM_ID ) );
    }

    /**
     * Returns all organisation units part of the organisation unit filter.
     */
    public List<DimensionalItemObject> getFilterOrganisationUnits()
    {
        return ImmutableList.copyOf( getFilterOptions( ORGUNIT_DIM_ID ) );
    }

    // -------------------------------------------------------------------------
    // Builder of immutable instances
    // -------------------------------------------------------------------------

    /**
     * Builder for {@link DataQueryParams} instances.
     */
    public static class Builder
    {
        private DataQueryParams params;
        
        protected Builder()
        {
            this.params = new DataQueryParams();
        }
        
        protected Builder( DataQueryParams query )
        {
            this.params = query.instance();
        }
        
        public Builder addDimension( DimensionalObject dimension )
        {
            this.params.addDimension( dimension );
            return this;
        }
        
        public Builder addDimensions( List<DimensionalObject> dimensions )
        {
            this.params.addDimensions( dimensions );
            return this;
        }

        public Builder replaceDimension( DimensionalObject dimension )
        {
            this.params.removeDimension( dimension.getDimension() );
            this.params.addDimension( dimension );
            return this;
        }

        public Builder withDimensions( List<DimensionalObject> dimensions )
        {
            this.params.dimensions = dimensions;
            return this;
        }
        
        public Builder withDimensionOptions( String dimension, List<DimensionalItemObject> options )
        {
            this.params.setDimensionOptions( dimension, options );
            return this;
        }

        public Builder removeDimension( String dimension )
        {
            this.params.dimensions.remove( new BaseDimensionalObject( dimension ) );
            return this;
        }

        public Builder removeDimensionOrFilter( String dimension )
        {
            this.params.dimensions.remove( new BaseDimensionalObject( dimension ) );
            this.params.filters.remove( new BaseDimensionalObject( dimension ) );            
            return this;
        }
        
        public Builder addOrSetDimensionOptions( String dimension, DimensionType type, String dimensionName, List<DimensionalItemObject> options )
        {
            this.params.setDimensionOptions( dimension, type, dimensionName, options );
            return this;
        }
                
        public Builder retainDataDimension( DataDimensionItemType itemType )
        {
            this.params.retainDataDimension( itemType );
            return this;
        }
        
        public Builder retainDataDimensions( DataDimensionItemType... itemTypes )
        {
            this.params.retainDataDimensions( itemTypes );
            return this;
        }
        
        public Builder retainDataDimensionReportingRates( ReportingRateMetric metric )
        {
            this.params.retainDataDimensionReportingRates( metric );
            return this;
        }

        public Builder pruneToDimensionType( DimensionType type )
        {
            this.params.pruneToDimensionType( type );
            return this;
        }
        
        public Builder withDataDimensionItems( List<? extends DimensionalItemObject> dataDimensionItems )
        {
            this.params.setDataDimensionOptions( null, dataDimensionItems );
            return this;
        }

        public Builder withIndicators( List<? extends DimensionalItemObject> indicators )
        {
            this.params.setDataDimensionOptions( DataDimensionItemType.INDICATOR, indicators );
            return this;
        }
        
        public Builder withDataElements( List<? extends DimensionalItemObject> dataElements )
        {
            this.params.setDataDimensionOptions( DataDimensionItemType.DATA_ELEMENT, dataElements );
            return this;
        }

        public Builder withReportingRates( List<? extends DimensionalItemObject> reportingRates )
        {
            this.params.setDataDimensionOptions( DataDimensionItemType.REPORTING_RATE, reportingRates );
            return this;
        }

        public Builder withProgramDataElements( List<? extends DimensionalItemObject> programDataElements )
        {
            this.params.setDataDimensionOptions( DataDimensionItemType.PROGRAM_DATA_ELEMENT, programDataElements );
            return this;
        }

        public Builder withProgramAttributes( List<? extends DimensionalItemObject> programAttributes )
        {
            this.params.setDataDimensionOptions( DataDimensionItemType.PROGRAM_ATTRIBUTE, programAttributes );
            return this;
        }
        
        public Builder withCategoryOptionCombos( List<? extends DimensionalItemObject> categoryOptionCombos )
        {
            this.params.setDimensionOptions( CATEGORYOPTIONCOMBO_DIM_ID, DimensionType.CATEGORY_OPTION_COMBO, null, asList( categoryOptionCombos ) );
            return this;
        }
        
        public Builder withAttributeOptionCombos( List<? extends DimensionalItemObject> attributeOptionCombos )
        {
            this.params.setDimensionOptions( ATTRIBUTEOPTIONCOMBO_DIM_ID, DimensionType.ATTRIBUTE_OPTION_COMBO, null, asList( attributeOptionCombos ) );
            return this;
        }
        
        public Builder withCategory( DataElementCategory category )
        {
            this.params.setDimensionOptions( category.getUid(), DimensionType.CATEGORY, null, new ArrayList<>( category.getItems() ) );
            return this;
        }
        
        public Builder withPeriods( List<? extends DimensionalItemObject> periods )
        {
            this.params.setDimensionOptions( PERIOD_DIM_ID, DimensionType.PERIOD, null, asList( periods ) );
            return this;
        }

        public Builder withPeriods( List<? extends DimensionalItemObject> periods, String periodType )
        {
            this.params.setDimensionOptions( PERIOD_DIM_ID, DimensionType.PERIOD, periodType.toLowerCase(), asList( periods ) );
            this.params.periodType = periodType;
            return this;
        }
        
        public Builder withPeriod( DimensionalItemObject period )
        {
            this.withPeriods( getList( period ) );
            return this;
        }

        public Builder withOrganisationUnits( List<? extends DimensionalItemObject> organisationUnits )
        {
            this.params.setDimensionOptions( ORGUNIT_DIM_ID, DimensionType.ORGANISATION_UNIT, null, asList( organisationUnits ) );
            return this;
        }
        
        public Builder withOrganisationUnit( DimensionalItemObject organisationUnit )
        {
            this.withOrganisationUnits( getList( organisationUnit ) );
            return this;
        }

        public Builder withValidationRules( List<? extends DimensionalItemObject> validationRules )
        {
            this.params.setDataDimensionOptions( DataDimensionItemType.VALIDATION_RULE, validationRules );
            return this;
        }
        
        public Builder addFilter( DimensionalObject filter )
        {
            this.params.addFilter( filter );
            return this;
        }
        
        public Builder addFilters( List<DimensionalObject> filters )
        {
            this.params.addFilters( filters );
            return this;
        }

        public Builder withFilters( List<DimensionalObject> filters )
        {
            this.params.filters = filters;
            return this;
        }

        public Builder removeFilter( String filter )
        {
            this.params.filters.remove( new BaseDimensionalObject( filter ) );
            return this;
        }
        
        public Builder withFilterPeriods( List<? extends DimensionalItemObject> periods )
        {
            this.params.setFilterOptions( PERIOD_DIM_ID, DimensionType.PERIOD, null, asList( periods ) );
            return this;
        }
        
        public Builder withFilterOrganisationUnits( List<? extends DimensionalItemObject> organisationUnits )
        {
            this.params.setFilterOptions( ORGUNIT_DIM_ID, DimensionType.ORGANISATION_UNIT, null, asList( organisationUnits ) );
            return this;
        }

        public Builder withMeasureCriteria( Map<MeasureFilter, Double> measureCriteria )
        {
            this.params.measureCriteria = measureCriteria;
            return this;
        }

        public Builder withPreAggregationMeasureCriteria( Map<MeasureFilter, Double> preAggregationMeasureCriteria )
        {
            this.params.preAggregateMeasureCriteria = preAggregationMeasureCriteria;
            return this;
        }
        
        public Builder withAggregationType( AnalyticsAggregationType aggregationType )
        {
            this.params.aggregationType = aggregationType;
            return this;
        }
        
        public Builder withSkipMeta( boolean skipMeta )
        {
            this.params.skipMeta = skipMeta;
            return this;
        }

        public Builder withSkipData( boolean skipData )
        {
            this.params.skipData = skipData;
            return this;
        }
        
        public Builder withSkipHeaders( boolean skipHeaders )
        {
            this.params.skipHeaders = skipHeaders;
            return this;
        }

        public Builder withSkipRounding( boolean skipRounding )
        {
            this.params.skipRounding = skipRounding;
            return this;
        }

        public Builder withCompletedOnly( boolean completedOnly )
        {
            this.params.completedOnly = completedOnly;
            return this;
        }

        public Builder withIgnoreLimit( boolean ignoreLimit )
        {
            this.params.ignoreLimit = ignoreLimit;
            return this;
        }

        public Builder withHierarchyMeta( boolean hierarchyMeta )
        {
            this.params.hierarchyMeta = hierarchyMeta;
            return this;
        }
        
        public Builder withHideEmptyRows( boolean hideEmptyRows )
        {
            this.params.hideEmptyRows = hideEmptyRows;
            return this;
        }
        
        public Builder withHideEmptyColumns( boolean hideEmptyColumns )
        {
            this.params.hideEmptyColumns = hideEmptyColumns;
            return this;
        }

        public Builder withShowHierarchy( boolean showHierarchy )
        {
            this.params.showHierarchy = showHierarchy;
            return this;
        }
        
        public Builder withIncludeNumDen( boolean includeNumDen )
        {
            this.params.includeNumDen = includeNumDen;
            return this;
        }
        
        public Builder withIncludePeriodStartEndDates( boolean includePeriodStartEndDates )
        {
            this.params.includePeriodStartEndDates = includePeriodStartEndDates;
            return this;
        }

        public Builder withIncludeMetadataDetails( boolean includeMetadataDetails )
        {
            this.params.includeMetadataDetails = includeMetadataDetails;
            return this;
        }

        public Builder withDisplayProperty( DisplayProperty displayProperty )
        {
            this.params.displayProperty = displayProperty;
            return this;
        }

        public Builder withOutputIdScheme( IdScheme outputIdScheme )
        {
            this.params.outputIdScheme = outputIdScheme;
            return this;
        }
        
        public Builder withOutputFormat( OutputFormat outputFormat )
        {
            this.params.outputFormat = outputFormat;
            return this;
        }
        
        public Builder withDuplicatesOnly( boolean duplicatesOnly )
        {
            this.params.duplicatesOnly = duplicatesOnly;
            return this;
        }
        
        public Builder withApprovalLevel( String approvalLevel )
        {
            this.params.approvalLevel = approvalLevel;
            return this;
        }
        
        public Builder withDataApprovalLevels( Map<OrganisationUnit, Integer> dataApprovalLevels )
        {
            this.params.dataApprovalLevels = dataApprovalLevels;
            return this;
        }
        
        public Builder withPeriodType( String periodType )
        {
            this.params.periodType = periodType;
            return this;
        }

        public Builder withDataPeriodType( PeriodType dataPeriodType )
        {
            this.params.dataPeriodType = dataPeriodType;
            return this;
        }
        
        public Builder withSkipPartitioning( boolean skipPartitioning )
        {
            this.params.skipPartitioning = skipPartitioning;
            return this;
        }
        
        public Builder withTimely( boolean timely )
        {
            this.params.timely = timely;
            return this;
        }
        
        public Builder withOrgUnitLevels( List<OrganisationUnitLevel> orgUnitLevels )
        {
            this.params.orgUnitLevels = orgUnitLevels;
            return this;
        }
        
        public Builder withRestrictByOrgUnitOpeningClosedDate( boolean restrictByOrgUnitOpeningClosedDate )
        {
            this.params.restrictByOrgUnitOpeningClosedDate = restrictByOrgUnitOpeningClosedDate;
            return this;
        }
        
        public Builder withRestrictByCategoryOptionStartEndDate( boolean restrictByCategoryOptionStartEndDate )
        {
            this.params.restrictByCategoryOptionStartEndDate = restrictByCategoryOptionStartEndDate;
            return this;
        }
        
        public Builder ignoreDataApproval()
        {
            this.params.dataApprovalLevels = new HashMap<>();
            return this;
        }
        
        public Builder addProcessingHint( ProcessingHint hint )
        {
            this.params.processingHints.add( hint );
            return this;
        }
        
        public Builder withCurrentUser( User currentUser )
        {
            this.params.currentUser = currentUser;
            return this;
        }
        
        public Builder withPartitions( Partitions partitions )
        {
            this.params.partitions = partitions;
            return this;
        }
        
        public Builder withTableName( String tableName )
        {
            this.params.tableName = tableName;
            return this;
        }
        
        public Builder withDataType( DataType dataType )
        {
            this.params.dataType = dataType;
            return this;
        }
        
        public Builder withStartDate( Date startDate )
        {
            this.params.startDate = startDate;
            return this;
        }
        
        public Builder withEndDate( Date endDate )
        {
            this.params.endDate = endDate;
            return this;
        }
        
        public Builder withOrder(SortOrder order)
        {
        		this.params.order = order;
        		return this;
        }
        
        public Builder withApiVersion( DhisApiVersion apiVersion )
        {
            this.params.apiVersion = apiVersion;
            return this;
        }
        
        public Builder withDataPeriodsForAggregationPeriods( ListMap<DimensionalItemObject, DimensionalItemObject> dataPeriodAggregationPeriodMap )
        {
            this.params.replaceAggregationPeriodsWithDataPeriods( dataPeriodAggregationPeriodMap );
            return this;
        }
        
        public Builder withEarliestStartDateLatestEndDate()
        {
            this.params.setEarliestStartDateLatestEndDate();
            return this;
        }
        
        public Builder withPeriodDimensionWithoutOptions()
        {
            this.params.setPeriodDimensionWithoutOptions();
            return this;
        }
        
        public DataQueryParams build()
        {
            return params;
        }
    }
}
