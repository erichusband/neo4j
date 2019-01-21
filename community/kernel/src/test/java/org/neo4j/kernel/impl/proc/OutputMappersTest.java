/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.proc;

import org.junit.jupiter.api.Test;

import java.util.Map;

import org.neo4j.internal.kernel.api.exceptions.ProcedureException;
import org.neo4j.kernel.impl.proc.OutputMappers.OutputMapper;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.neo4j.internal.kernel.api.procs.FieldSignature.outputField;
import static org.neo4j.internal.kernel.api.procs.Neo4jTypes.NTString;

@SuppressWarnings( "WeakerAccess" )
public class OutputMappersTest
{
    public static class SingleStringFieldRecord
    {
        public String name;

        public SingleStringFieldRecord( String name )
        {
            this.name = name;
        }
    }

    public static class UnmappableRecord
    {
        public UnmappableRecord wat;
    }

    public static class RecordWithPrivateField
    {
        private String wat;
    }

    public static class RecordWithNonStringKeyMap
    {
        public Map<RecordWithNonStringKeyMap,Object> wat;
    }

    public static class RecordWithStaticFields
    {
        public static String skipMePublic;
        public String includeMe;
        private static String skipMePrivate;

        public RecordWithStaticFields( String val )
        {
            this.includeMe = val;
        }
    }

    public static class RecordWithDeprecatedFields
    {
        @Deprecated
        public String deprecated;
        public String replacement;
        @Deprecated
        public String alsoDeprecated;
    }

    @Test
    void shouldMapSimpleRecordWithString() throws Throwable
    {
        // When
        OutputMapper mapper = mapper( SingleStringFieldRecord.class );

        // Then
        assertThat(
                mapper.signature(),
                contains( outputField( "name", NTString ) )
        );
        assertThat(
                asList( mapper.apply( new SingleStringFieldRecord( "hello, world!" ) ) ),
                contains( "hello, world!" )
        );
    }

    @Test
    void shouldSkipStaticFields() throws Throwable
    {
        // When
        OutputMapper mapper = mapper( RecordWithStaticFields.class );

        // Then
        assertThat(
                mapper.signature(),
                contains( outputField( "includeMe", NTString ) )
        );
        assertThat(
                asList( mapper.apply( new RecordWithStaticFields( "hello, world!" ) ) ),
                contains( "hello, world!" )
        );
    }

    @Test
    void shouldNoteDeprecatedFields() throws Exception
    {
        // when
        OutputMapper mapper = mapper( RecordWithDeprecatedFields.class );

        // then
        assertThat( mapper.signature(), containsInAnyOrder(
                outputField( "deprecated", NTString, true ),
                outputField( "alsoDeprecated", NTString, true ),
                outputField( "replacement", NTString, false ) ) );
    }

    @Test
    void shouldGiveHelpfulErrorOnUnmappable()
    {
        ProcedureException exception = assertThrows( ProcedureException.class, () -> mapper( UnmappableRecord.class ) );
        assertThat( exception.getMessage(), startsWith( "Field `wat` in record `UnmappableRecord` cannot be converted to a Neo4j type: " +
                "Don't know how to map `org.neo4j.kernel.impl.proc.OutputMappersTest$UnmappableRecord`" ) );
    }

    @Test
    void shouldGiveHelpfulErrorOnPrivateField()
    {
        ProcedureException exception = assertThrows( ProcedureException.class, () -> mapper( RecordWithPrivateField.class ) );
        assertThat( exception.getMessage(), startsWith( "Field `wat` in record `RecordWithPrivateField` cannot be accessed. " +
                "Please ensure the field is marked as `public`." ) );
    }

    @Test
    void shouldGiveHelpfulErrorOnMapWithNonStringKeyMap()
    {
        ProcedureException exception = assertThrows( ProcedureException.class, () -> mapper( RecordWithNonStringKeyMap.class ) );
        assertThat( exception.getMessage(), equalTo( "Field `wat` in record `RecordWithNonStringKeyMap` cannot be converted " +
                "to a Neo4j type: Maps are required to have `String` keys - but this map " +
                "has `org.neo4j.kernel.impl.proc.OutputMappersTest$RecordWithNonStringKeyMap` keys." ) );
    }

    @Test
    void shouldWarnAgainstStdLibraryClassesSinceTheseIndicateUserError() throws Throwable
    {
        // Impl note: We may want to change this behavior and actually allow procedures to return `Long` etc,
        //            with a default column name. So Stream<Long> would become records like (out: Long)
        //            Drawback of that is that it'd cause cognitive dissonance, it's not obvious what's a record
        //            and what is a primitive value..

        ProcedureException exception = assertThrows( ProcedureException.class, () -> mapper(Long.class) );
        assertThat( exception.getMessage(), equalTo( String.format("Procedures must return a Stream of records, where a record is a concrete class%n" +
                "that you define, with public non-final fields defining the fields in the record.%n" +
                "If you''d like your procedure to return `Long`, you could define a record class like:%n" +
                "public class Output '{'%n" +
                "    public Long out;%n" +
                "'}'%n" +
                "%n" +
                "And then define your procedure as returning `Stream<Output>`." ) ) );
    }

    private OutputMapper mapper( Class<?> clazz ) throws ProcedureException
    {
        return new OutputMappers( new TypeMappers() ).mapper( clazz );
    }

}
