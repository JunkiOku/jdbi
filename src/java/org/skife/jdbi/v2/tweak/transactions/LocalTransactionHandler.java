/*
 * Copyright 2004-2006 Brian McCallister
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.skife.jdbi.v2.tweak.transactions;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.exceptions.TransactionException;
import org.skife.jdbi.v2.tweak.TransactionHandler;

import java.sql.SQLException;
import java.sql.Connection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This <code>TransactionHandler</code> uses local JDBC transactions
 * demarcated explicitely on the handle and passed through to be handled
 * directly on the JDBC Connection instance.
 */
public class LocalTransactionHandler implements TransactionHandler
{
    private ConcurrentHashMap<Handle, Boolean> initialAutoCommits = new ConcurrentHashMap<Handle, Boolean>();

    /**
     * Called when a transaction is started
     */
    public void begin(Handle handle)
    {
        try
        {
            boolean initial = handle.getConnection().getAutoCommit();
            initialAutoCommits.put(handle, initial);
            handle.getConnection().setAutoCommit(false);
        }
        catch (SQLException e)
        {
            throw new TransactionException("Failed to start transaction", e);
        }
    }

    /**
     * Called when a transaction is committed
     */
    public void commit(Handle handle)
    {
        try
        {
            handle.getConnection().commit();
            final Boolean auto = initialAutoCommits.remove(handle);
            if (auto != null) {
                handle.getConnection().setAutoCommit(auto);
            }
        }
        catch (SQLException e)
        {
            throw new TransactionException("Failed to commit transaction", e);
        }
        finally
        {
            // prevent memory leak if commit throws an exception
            if (initialAutoCommits.containsKey(handle)) {
                initialAutoCommits.remove(handle);
            }
        }
    }

    /**
     * Called when a transaction is rolled back
     */
    public void rollback(Handle handle)
    {
        try
        {
            handle.getConnection().rollback();
            final Boolean auto = initialAutoCommits.remove(handle);
            if (auto != null) {
                handle.getConnection().setAutoCommit(auto);
            }
        }
        catch (SQLException e)
        {
            throw new TransactionException("Failed to rollback transaction", e);
        }
        finally
        {
            // prevent memory leak if rollback throws an exception
            if (initialAutoCommits.containsKey(handle)) {
                initialAutoCommits.remove(handle);
            }
        }
    }

    /**
     * Called to test if a handle is in a transaction
     */
    public boolean isInTransaction(Handle handle)
    {
        try
        {
            return !handle.getConnection().getAutoCommit();
        }
        catch (SQLException e)
        {
            throw new TransactionException("Failed to test for transaction status", e);
        }
    }
}
