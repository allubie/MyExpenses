package org.totschnig.myexpenses.test.sync;

import android.content.ContentProviderOperation;

import org.junit.Before;
import org.junit.Test;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.CurrencyContext;
import org.totschnig.myexpenses.provider.TransactionProvider;
import org.totschnig.myexpenses.sync.SyncDelegate;
import org.totschnig.myexpenses.sync.json.TransactionChange;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

//@RequiresApi(api = Build.VERSION_CODES.M)
public class SyncAdapterWriteToDbTest {
  private SyncDelegate syncDelegate;
  private ArrayList<ContentProviderOperation> ops;

  @Before
  public void setup() {
    //Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);

    syncDelegate = new SyncDelegate(mock(CurrencyContext.class));
    syncDelegate.account = new Account();
    ops = new ArrayList<>();
  }

  @Test
  public void createdChangeShouldBeCollectedAsInsertOperation() {
    TransactionChange change = TransactionChange.builder()
        .setType(TransactionChange.Type.created)
        .setUuid("any")
        .setCurrentTimeStamp()
        .setAmount(123L)
        .build();
    syncDelegate.collectOperations(change, ops, -1);
    assertEquals(1, ops.size());
    assertTrue(ops.get(0).isInsert());
  }

  @Test
  public void updatedChangeShouldBeCollectedAsUpdateOperationWithoutTag() {
    TransactionChange change = TransactionChange.builder()
        .setType(TransactionChange.Type.updated)
        .setUuid("any")
        .setCurrentTimeStamp()
        .setAmount(123L)
        .build();
    syncDelegate.collectOperations(change, ops, -1);
    assertEquals(2, ops.size());
    assertTrue(ops.get(0).isUpdate());
    assertTrue(ops.get(1).isDelete());
    assertEquals(TransactionProvider.TRANSACTIONS_TAGS_URI, ops.get(1).getUri());
  }

  @Test
  public void createChangeWithTag() {
    TransactionChange change = TransactionChange.builder()
        .setType(TransactionChange.Type.created)
        .setUuid("any")
        .setCurrentTimeStamp()
        .setAmount(123L)
        .setTags(Collections.singletonList("tag"))
        .build();
    syncDelegate.collectOperations(change, ops, -1);

    assertEquals(2, ops.size());
    assertTrue(ops.get(0).isInsert());
    assertEquals(TransactionProvider.TRANSACTIONS_TAGS_URI, ops.get(1).getUri());
    assertTrue(ops.get(1).isInsert());
  }

  @Test
  public void deletedChangeShouldBeCollectedAsDeleteOperation() {
    TransactionChange change = TransactionChange.builder()
        .setType(TransactionChange.Type.deleted)
        .setUuid("any")
        .setCurrentTimeStamp()
        .build();
    syncDelegate.collectOperations(change, ops, -1);
    assertEquals(1, ops.size());
    assertTrue(ops.get(0).isDelete());
  }

}
