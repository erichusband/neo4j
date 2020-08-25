/*
 * Copyright (c) 2002-2020 "Neo4j,"
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
package org.neo4j.internal.recordstorage;

import org.neo4j.internal.id.IdGenerator;
import org.neo4j.internal.id.IdGeneratorFactory;
import org.neo4j.kernel.impl.store.NeoStores;
import org.neo4j.kernel.impl.store.PropertyStore;
import org.neo4j.kernel.impl.store.RecordStore;
import org.neo4j.kernel.impl.store.record.LabelTokenRecord;
import org.neo4j.kernel.impl.store.record.NodeRecord;
import org.neo4j.kernel.impl.store.record.PropertyKeyTokenRecord;
import org.neo4j.kernel.impl.store.record.PropertyRecord;
import org.neo4j.kernel.impl.store.record.RelationshipGroupRecord;
import org.neo4j.kernel.impl.store.record.RelationshipRecord;
import org.neo4j.kernel.impl.store.record.RelationshipTypeTokenRecord;
import org.neo4j.kernel.impl.store.record.SchemaRecord;

public class DirectRecordAccessSet implements RecordAccessSet
{
    private final DirectRecordAccess<NodeRecord> nodeRecords;
    private final DirectRecordAccess<PropertyRecord> propertyRecords;
    private final DirectRecordAccess<RelationshipRecord> relationshipRecords;
    private final DirectRecordAccess<RelationshipGroupRecord> relationshipGroupRecords;
    private final DirectRecordAccess<PropertyKeyTokenRecord> propertyKeyTokenRecords;
    private final DirectRecordAccess<RelationshipTypeTokenRecord> relationshipTypeTokenRecords;
    private final DirectRecordAccess<LabelTokenRecord> labelTokenRecords;
    private final DirectRecordAccess<?>[] all;
    private final IdGeneratorFactory idGeneratorFactory;

    public DirectRecordAccessSet( NeoStores neoStores, IdGeneratorFactory idGeneratorFactory )
    {
        RecordStore<NodeRecord> nodeStore = neoStores.getNodeStore();
        PropertyStore propertyStore = neoStores.getPropertyStore();
        RecordStore<RelationshipRecord> relationshipStore = neoStores.getRelationshipStore();
        RecordStore<RelationshipGroupRecord> relationshipGroupStore = neoStores.getRelationshipGroupStore();
        RecordStore<PropertyKeyTokenRecord> propertyKeyTokenStore = neoStores.getPropertyKeyTokenStore();
        RecordStore<RelationshipTypeTokenRecord> relationshipTypeTokenStore = neoStores.getRelationshipTypeTokenStore();
        RecordStore<LabelTokenRecord> labelTokenStore = neoStores.getLabelTokenStore();
        Loaders loaders = new Loaders( neoStores );
        nodeRecords = new DirectRecordAccess<>( nodeStore, loaders.nodeLoader() );
        propertyRecords = new DirectRecordAccess<>( propertyStore, loaders.propertyLoader() );
        relationshipRecords = new DirectRecordAccess<>( relationshipStore, loaders.relationshipLoader() );
        relationshipGroupRecords = new DirectRecordAccess<>(
                relationshipGroupStore, loaders.relationshipGroupLoader() );
        propertyKeyTokenRecords = new DirectRecordAccess<>( propertyKeyTokenStore, loaders.propertyKeyTokenLoader() );
        relationshipTypeTokenRecords = new DirectRecordAccess<>(
                relationshipTypeTokenStore, loaders.relationshipTypeTokenLoader() );
        labelTokenRecords = new DirectRecordAccess<>( labelTokenStore, loaders.labelTokenLoader() );
        all = new DirectRecordAccess[] {
                nodeRecords, propertyRecords, relationshipRecords, relationshipGroupRecords,
                propertyKeyTokenRecords, relationshipTypeTokenRecords, labelTokenRecords
        };
        this.idGeneratorFactory = idGeneratorFactory;
    }

    @Override
    public RecordAccess<NodeRecord> getNodeRecords()
    {
        return nodeRecords;
    }

    @Override
    public RecordAccess<PropertyRecord> getPropertyRecords()
    {
        return propertyRecords;
    }

    @Override
    public RecordAccess<RelationshipRecord> getRelRecords()
    {
        return relationshipRecords;
    }

    @Override
    public RecordAccess<RelationshipGroupRecord> getRelGroupRecords()
    {
        return relationshipGroupRecords;
    }

    @Override
    public RecordAccess<SchemaRecord> getSchemaRuleChanges()
    {
        throw new UnsupportedOperationException( "Not needed. Implement if needed" );
    }

    @Override
    public RecordAccess<PropertyKeyTokenRecord> getPropertyKeyTokenChanges()
    {
        return propertyKeyTokenRecords;
    }

    @Override
    public RecordAccess<LabelTokenRecord> getLabelTokenChanges()
    {
        return labelTokenRecords;
    }

    @Override
    public RecordAccess<RelationshipTypeTokenRecord> getRelationshipTypeTokenChanges()
    {
        return relationshipTypeTokenRecords;
    }

    public void commit()
    {
        for ( DirectRecordAccess<?> access : all )
        {
            access.commit();
        }
        idGeneratorFactory.visit( IdGenerator::markHighestWrittenAtHighId );
    }

    @Override
    public boolean hasChanges()
    {
        for ( DirectRecordAccess<?> access : all )
        {
            if ( access.changeSize() > 0 )
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public int changeSize()
    {
        int total = 0;
        for ( DirectRecordAccess<?> access : all )
        {
            total += access.changeSize();
        }
        return total;
    }
}
