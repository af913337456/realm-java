/*
 * Copyright 2016 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.realm;

import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.realm.annotations.RealmModule;
import io.realm.entities.AllJavaTypes;
import io.realm.entities.Dog;
import io.realm.exceptions.RealmException;
import io.realm.rule.RunInLooperThread;
import io.realm.sync.permissions.ClassPermissions;
import io.realm.sync.permissions.Permission;
import io.realm.sync.permissions.RealmPermissions;
import io.realm.sync.permissions.RealmPrivileges;
import io.realm.sync.permissions.Role;

import static io.realm.util.SyncTestUtils.createTestUser;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class ObjectLevelPermissionsTest {

    private static String REALM_URI = "realm://objectserver.realm.io/~/default";

    private SyncConfiguration configuration;
    private SyncUser user;

    @Rule
    public final TestSyncConfigurationFactory configFactory = new TestSyncConfigurationFactory();

    @Rule
    public final RunInLooperThread looperThread = new RunInLooperThread();
    private Realm realm;
    private DynamicRealm dynamicRealm;

    @RealmModule(classes = { AllJavaTypes.class })
    public static class TestModule {
    }

    @Before
    public void setUp() {
        user = createTestUser();
        configuration = new SyncConfiguration.Builder(user, REALM_URI)
                .partialRealm()
                .modules(new TestModule())
                .build();
        realm = Realm.getInstance(configuration);
        dynamicRealm = DynamicRealm.getInstance(configuration);
    }

    @After
    public void tearDown() {
        if (realm != null && !realm.isClosed()) {
            realm.close();
        }
        if (dynamicRealm != null && !dynamicRealm.isClosed()) {
            dynamicRealm.close();
        }
    }

    @Test
    public void getPrivileges_realm_localDefaults() {
        RealmPrivileges privileges = realm.getPrivileges();
        assertFullAccess(privileges);

        privileges = dynamicRealm.getPrivileges();
        assertFullAccess(privileges);
    }

    @Test
    public void getPrivileges_class_localDefaults() {
        RealmPrivileges privileges = realm.getPrivileges(AllJavaTypes.class);
        assertDefaultAccess(privileges);

        privileges = dynamicRealm.getPrivileges(AllJavaTypes.CLASS_NAME);
        assertDefaultAccess(privileges);
    }

    @Test
    public void getPrivileges_object_localDefaults() {
        realm.beginTransaction();
        AllJavaTypes obj = realm.createObject(AllJavaTypes.class, 0);
        realm.commitTransaction();
        assertDefaultAccess(realm.getPrivileges(obj));

        dynamicRealm.beginTransaction();
        DynamicRealmObject dynamicObject = dynamicRealm.createObject(AllJavaTypes.CLASS_NAME, 1);
        dynamicRealm.commitTransaction();
        assertDefaultAccess(dynamicRealm.getPrivileges(dynamicObject));
    }

    @Test
    public void getPrivileges_closedRealmThrows() {
        realm.close();
        try {
            realm.getPrivileges();
            fail();
        } catch(IllegalStateException ignored) {
        }

        try {
            realm.getPrivileges(AllJavaTypes.class);
            fail();
        } catch(IllegalStateException ignored) {
        }

        try {
            //noinspection ConstantConditions
            realm.getPrivileges((RealmModel) null);
            fail();
        } catch(IllegalStateException ignored) {
        }

        dynamicRealm.close();
        try {
            dynamicRealm.getPrivileges();
            fail();
        } catch(IllegalStateException ignored) {
        }

        try {
            dynamicRealm.getPrivileges(AllJavaTypes.CLASS_NAME);
            fail();
        } catch(IllegalStateException ignored) {
        }

        try {
            //noinspection ConstantConditions
            dynamicRealm.getPrivileges((RealmModel) null);
            fail();
        } catch(IllegalStateException ignored) {
        }
    }

    @Test
    public void getPrivileges_wrongThreadThrows() throws InterruptedException {
        Thread thread = new Thread(() -> {
            try {
                realm.getPrivileges();
                fail();
            } catch(IllegalStateException ignored) {
            }

            try {
                realm.getPrivileges(AllJavaTypes.class);
                fail();
            } catch(IllegalStateException ignored) {
            }

            try {
                //noinspection ConstantConditions
                realm.getPrivileges((RealmModel) null);
                fail();
            } catch(IllegalStateException ignored) {
            }

            try {
                dynamicRealm.getPrivileges();
                fail();
            } catch(IllegalStateException ignored) {
            }

            try {
                dynamicRealm.getPrivileges(AllJavaTypes.CLASS_NAME);
                fail();
            } catch(IllegalStateException ignored) {
            }

            try {
                //noinspection ConstantConditions
                dynamicRealm.getPrivileges((RealmModel) null);
                fail();
            } catch(IllegalStateException ignored) {
            }
        });
        thread.start();
        thread.join(TestHelper.STANDARD_WAIT_SECS * 1000);
    }

    @Test
    public void getPrivileges_class_notPartofSchemaThrows() {
        try {
            realm.getPrivileges(Dog.class);
            fail();
        } catch (RealmException ignore) {
        }

        try {
            dynamicRealm.getPrivileges("Dog");
            fail();
        } catch (RealmException ignore) {
        }
    }

    @Test
    public void getPrivileges_class_nullThrows() {
        try {
            //noinspection ConstantConditions
            realm.getPrivileges((Class<? extends RealmModel>) null);
            fail();
        } catch (IllegalArgumentException ignore) {
        }

        try {
            //noinspection ConstantConditions
            dynamicRealm.getPrivileges((String) null);
            fail();
        } catch (IllegalArgumentException ignore) {
        }
    }

    @Test
    public void getPrivileges_object_nullThrows() {
        try {
            //noinspection ConstantConditions
            realm.getPrivileges((RealmModel) null);
            fail();
        } catch (IllegalArgumentException ignore) {
        }

        try {
            //noinspection ConstantConditions
            dynamicRealm.getPrivileges((DynamicRealmObject) null);
            fail();
        } catch (IllegalArgumentException ignore) {
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void getPrivileges_object_unmanagedThrows() {
        // DynamicRealm do not support unmanaged DynamicRealmObjects
        realm.getPrivileges(new AllJavaTypes(0));
    }

    @Test
    public void getPrivileges_object_wrongRealmThrows() {
        Realm otherRealm = Realm.getInstance(configFactory.createConfiguration("other"));
        otherRealm.beginTransaction();
        AllJavaTypes obj = otherRealm.createObject(AllJavaTypes.class, 0);
        try {
            realm.getPrivileges(obj);
            fail();
        } catch (IllegalArgumentException ignored) {
        } finally {
            otherRealm.close();
        }
    }


    @Test
    public void getPermissions() {
        // Typed RealmPermissions
        RealmPermissions realmPermissions = realm.getPermissions();
        RealmList<Permission> list = realmPermissions.getPermissions();
        assertEquals(1, list.size());
        assertEquals("everyone", list.first().getRole().getName());
        assertFullAccess(list.first());

//        // FIXME: Dynamic RealmPermissions - Until support is enabled
//        realmPermissions = dynamicRealm.getPermissions();
//        list = realmPermissions.getPermissions();
//        assertEquals(1, list.size());
//        assertEquals("everyone", list.first().getRole().getName());
//        assertFullAccess(list.first());
    }

    @Test
    public void getPermissions_wrongThreadThrows() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                realm.getPermissions();
                fail();
            } catch (IllegalStateException ignore) {
            }

// FIXME: Disabled until support is enabled
//            try {
//                dynamicRealm.getPermissions();
//                fail();
//            } catch (IllegalStateException ignore) {
//            }
        });
        t.start();
        t.join(TestHelper.STANDARD_WAIT_SECS * 1000);
    }

    @Test
    public void getPermissions_closedRealmThrows() {
        realm.close();
        try {
            realm.getPermissions();
            fail();
        } catch (IllegalStateException ignore) {
        }

// FIXME Disabled until support is enabled
//        dynamicRealm.close();
//        try {
//            dynamicRealm.getPermissions();
//            fail();
//        } catch (IllegalStateException ignore) {
//        }
    }

    @Test
    public void getClassPermissions() {
        // Typed RealmPermissions
        ClassPermissions classPermissions = realm.getPermissions(AllJavaTypes.class);
        assertEquals("AllJavaTypes", classPermissions.getName());
        RealmList<Permission> list = classPermissions.getPermissions();
        assertEquals(1, list.size());
        assertEquals("everyone", list.first().getRole().getName());
        assertDefaultAccess(list.first());

        // FIXME: Dynamic RealmPermissions - Disabled until support is enabled
//        classPermissions = dynamicRealm.getPermissions(AllJavaTypes.CLASS_NAME);
//        assertEquals("AllJavaTypes", classPermissions.getName());
//        list = classPermissions.getPermissions();
//        assertEquals(1, list.size());
//        assertEquals("everyone", list.first().getRole().getName());
//        assertDefaultAccess(list.first());
    }

    @Test
    public void getClassPermissions_wrongThreadThrows() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                realm.getPermissions(AllJavaTypes.class);
                fail();
            } catch (IllegalStateException ignore) {
            }

// FIXME: Disabled until support is enabled
//            try {
//                dynamicRealm.getPermissions(AllJavaTypes.CLASS_NAME);
//                fail();
//            } catch (IllegalStateException ignore) {
//            }
        });
        t.start();
        t.join(TestHelper.STANDARD_WAIT_SECS * 1000);
    }

    @Test
    public void getClassPermissions_closedRealmThrows() {
        realm.close();
        try {
            realm.getPermissions(AllJavaTypes.class);
            fail();
        } catch (IllegalStateException ignore) {
        }

// FIXME: Disabled until support is enabled
//        dynamicRealm.close();
//        try {
//            dynamicRealm.getPermissions(AllJavaTypes.CLASS_NAME);
//            fail();
//        } catch (IllegalStateException ignore) {
//        }
    }

    @Test
    public void getRoles() {
        RealmResults<Role> roles = realm.getRoles();
        assertEquals(1, roles.size());
        Role role = roles.first();
        assertEquals("everyone", role.getName());
        assertTrue(role.hasMember(user.getIdentity()));
    }

    @Test
    public void getRoles_wrongThreadThrows() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                realm.getRoles();
                fail();
            } catch (IllegalStateException ignore) {
            }
        });
        t.start();
        t.join(TestHelper.STANDARD_WAIT_SECS * 1000);

    }

    @Test
    public void getRoles_closedRealmThrows() {
        realm.close();
        try {
            realm.getRoles();
            fail();
        } catch (IllegalStateException ignore) {
        }

// FIXME: Until support is enabled
//        dynamicRealm.close();
//        try {
//            dynamicRealm.getRoles();
//            fail();
//        } catch (IllegalStateException ignore) {
//        }
    }

    private void assertDefaultAccess(RealmPrivileges privileges) {
        assertFalse(privileges.canCreate());
        assertTrue(privileges.canRead());
        assertTrue(privileges.canUpdate());
        assertFalse(privileges.canDelete());
        assertTrue(privileges.canQuery());
        assertTrue(privileges.canSetPermissions());
        assertTrue(privileges.canModifySchema());
    }

    private void assertDefaultAccess(Permission permission) {
        assertFalse(permission.canCreate());
        assertTrue(permission.canRead());
        assertTrue(permission.canUpdate());
        assertFalse(permission.canDelete());
        assertTrue(permission.canQuery());
        assertTrue(permission.canSetPermissions());
        assertTrue(permission.canModifySchema());
    }

    private void assertFullAccess(RealmPrivileges privileges) {
        assertTrue(privileges.canCreate());
        assertTrue(privileges.canRead());
        assertTrue(privileges.canUpdate());
        assertTrue(privileges.canDelete());
        assertTrue(privileges.canQuery());
        assertTrue(privileges.canSetPermissions());
        assertTrue(privileges.canModifySchema());
    }

    private void assertFullAccess(Permission permission) {
        assertTrue(permission.canCreate());
        assertTrue(permission.canRead());
        assertTrue(permission.canUpdate());
        assertTrue(permission.canDelete());
        assertTrue(permission.canQuery());
        assertTrue(permission.canSetPermissions());
        assertTrue(permission.canModifySchema());
    }

    private void assertNoAccess(RealmPrivileges privileges) {
        assertFalse(privileges.canCreate());
        assertFalse(privileges.canRead());
        assertFalse(privileges.canUpdate());
        assertFalse(privileges.canDelete());
        assertFalse(privileges.canQuery());
        assertFalse(privileges.canSetPermissions());
        assertFalse(privileges.canModifySchema());
    }
}
