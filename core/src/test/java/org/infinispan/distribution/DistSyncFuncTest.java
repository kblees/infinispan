package org.infinispan.distribution;

import org.infinispan.Cache;
import org.infinispan.commands.write.ClearCommand;
import org.infinispan.commands.write.PutKeyValueCommand;
import org.infinispan.commands.write.RemoveCommand;
import org.infinispan.commands.write.ReplaceCommand;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "distribution.DistSyncFuncTest")
public class DistSyncFuncTest extends BaseDistFunctionalTest {

   public DistSyncFuncTest() {
      sync = true;
      tx = false;
   }

   public void testBasicDistribution() {
      for (Cache<Object, String> c : caches) assert c.isEmpty();

      getOwners("k1")[0].put("k1", "value");

      asyncWait("k1", PutKeyValueCommand.class, getNonOwners("k1"));

      for (Cache<Object, String> c : caches) {
         if (isOwner(c, "k1")) {
            assertIsInContainerImmortal(c, "k1");
         } else {
            assertIsNotInL1(c, "k1");
         }
      }

      // should be available everywhere!
      assertOnAllCachesAndOwnership("k1", "value");

      // and should now be in L1

      for (Cache<Object, String> c : caches) {
         if (isOwner(c, "k1")) {
            assertIsInContainerImmortal(c, "k1");
         } else {
            assertIsInL1(c, "k1");
         }
      }
   }

   public void testPutFromNonOwner() {
      initAndTest();
      Cache<Object, String> nonOwner = getFirstNonOwner("k1");
      System.out.println("Non-owner address is " + nonOwner.getCacheManager().getAddress());

      Object retval = nonOwner.put("k1", "value2");
      asyncWait("k1", PutKeyValueCommand.class, getSecondNonOwner("k1"));

      assert "value".equals(retval);
      assertOnAllCachesAndOwnership("k1", "value2");
   }

   public void testPutIfAbsentFromNonOwner() {
      initAndTest();
      Object retval = getFirstNonOwner("k1").putIfAbsent("k1", "value2");

      assert "value".equals(retval);

      assertOnAllCachesAndOwnership("k1", "value");

      c1.clear();
      asyncWait(null, ClearCommand.class);

      retval = getFirstNonOwner("k1").putIfAbsent("k1", "value2");
      asyncWait("k1", PutKeyValueCommand.class, getSecondNonOwner("k1"));
      assert null == retval;

      assertOnAllCachesAndOwnership("k1", "value2");
   }

   public void testRemoveFromNonOwner() {
      initAndTest();
      Object retval = getFirstNonOwner("k1").remove("k1");
      asyncWait("k1", RemoveCommand.class, getSecondNonOwner("k1"));
      if (sync) assert "value".equals(retval);

      assertOnAllCachesAndOwnership("k1", null);
   }

   public void testConditionalRemoveFromNonOwner() {
      initAndTest();
      boolean retval = getFirstNonOwner("k1").remove("k1", "value2");
      assert !retval : "Should not have removed entry";

      assertOnAllCachesAndOwnership("k1", "value");

      retval = getFirstNonOwner("k1").remove("k1", "value");
      asyncWait("k1", RemoveCommand.class, getSecondNonOwner("k1"));
      assert retval : "Should have removed entry";

      assertOnAllCachesAndOwnership("k1", null);
   }

   public void testReplaceFromNonOwner() {
      initAndTest();
      Object retval = getFirstNonOwner("k1").replace("k1", "value2");
      assert "value".equals(retval);

      asyncWait("k1", ReplaceCommand.class, getSecondNonOwner("k1"));

      assertOnAllCachesAndOwnership("k1", "value2");

      c1.clear();
      asyncWait(null, ClearCommand.class);

      retval = getFirstNonOwner("k1").replace("k1", "value2");
      assert retval == null;

      assertOnAllCachesAndOwnership("k1", null);
   }

   public void testConditionalReplaceFromNonOwner() {
      initAndTest();
      Cache<Object, String> nonOwner = getFirstNonOwner("k1");
      boolean retval = nonOwner.replace("k1", "valueX", "value2");
      assert !retval : "Should not have replaced";

      assertOnAllCachesAndOwnership("k1", "value");

      assert !nonOwner.getAdvancedCache().getComponentRegistry().getComponent(DistributionManager.class).isLocal("k1");
      retval = nonOwner.replace("k1", "value", "value2");
      asyncWait("k1", ReplaceCommand.class, getSecondNonOwner("k1"));
      assert retval : "Should have replaced";

      assertOnAllCachesAndOwnership("k1", "value2");
   }

   public void testClear() {
      for (Cache<Object, String> c : caches) assert c.isEmpty();

      for (int i = 0; i < 10; i++) {
         getOwners("k" + i)[0].put("k" + i, "value" + i);
         asyncWait("k" + i, PutKeyValueCommand.class, getNonOwners("k" + i));
      }

      // this will fill up L1 as well
      for (int i = 0; i < 10; i++) assertOnAllCachesAndOwnership("k" + i, "value" + i);

      for (Cache<Object, String> c : caches) assert !c.isEmpty();

      c1.clear();
      asyncWait(null, ClearCommand.class);

      for (Cache<Object, String> c : caches) assert c.isEmpty();
   }
}
