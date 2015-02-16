/*******************************************************************************

  * Copyright (c) 2015. Rick Hightower, Geoff Chandler
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *  		http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  *  ________ __________.______________
  *  \_____  \\______   \   \__    ___/
  *   /  / \  \|    |  _/   | |    |  ______
  *  /   \_/.  \    |   \   | |    | /_____/
  *  \_____\ \_/______  /___| |____|
  *         \__>      \/
  *  ___________.__                  ____.                        _____  .__                                             .__
  *  \__    ___/|  |__   ____       |    |____ ___  _______      /     \ |__| ___________  ____  ______ ______________  _|__| ____  ____
  *    |    |   |  |  \_/ __ \      |    \__  \\  \/ /\__  \    /  \ /  \|  |/ ___\_  __ \/  _ \/  ___// __ \_  __ \  \/ /  |/ ___\/ __ \
  *    |    |   |   Y  \  ___/  /\__|    |/ __ \\   /  / __ \_ /    Y    \  \  \___|  | \(  <_> )___ \\  ___/|  | \/\   /|  \  \__\  ___/
  *    |____|   |___|  /\___  > \________(____  /\_/  (____  / \____|__  /__|\___  >__|   \____/____  >\___  >__|    \_/ |__|\___  >___  >
  *                  \/     \/                \/           \/          \/        \/                 \/     \/                    \/    \/
  *  .____    ._____.
  *  |    |   |__\_ |__
  *  |    |   |  || __ \
  *  |    |___|  || \_\ \
  *  |_______ \__||___  /
  *          \/       \/
  *       ____. _________________    _______         __      __      ___.     _________              __           __      _____________________ ____________________
  *      |    |/   _____/\_____  \   \      \       /  \    /  \ ____\_ |__  /   _____/ ____   ____ |  | __ _____/  |_    \______   \_   _____//   _____/\__    ___/
  *      |    |\_____  \  /   |   \  /   |   \      \   \/\/   // __ \| __ \ \_____  \ /  _ \_/ ___\|  |/ // __ \   __\    |       _/|    __)_ \_____  \   |    |
  *  /\__|    |/        \/    |    \/    |    \      \        /\  ___/| \_\ \/        (  <_> )  \___|    <\  ___/|  |      |    |   \|        \/        \  |    |
  *  \________/_______  /\_______  /\____|__  / /\    \__/\  /  \___  >___  /_______  /\____/ \___  >__|_ \\___  >__| /\   |____|_  /_______  /_______  /  |____|
  *                   \/         \/         \/  )/         \/       \/    \/        \/            \/     \/    \/     )/          \/        \/        \/
  *  __________           __  .__              __      __      ___.
  *  \______   \ ____   _/  |_|  |__   ____   /  \    /  \ ____\_ |__
  *  |    |  _// __ \  \   __\  |  \_/ __ \  \   \/\/   // __ \| __ \
  *   |    |   \  ___/   |  | |   Y  \  ___/   \        /\  ___/| \_\ \
  *   |______  /\___  >  |__| |___|  /\___  >   \__/\  /  \___  >___  /
  *          \/     \/             \/     \/         \/       \/    \/
  *
  * QBit - The Microservice lib for Java : JSON, WebSocket, REST. Be The Web!
  *  http://rick-hightower.blogspot.com/2014/12/rise-of-machines-writing-high-speed.html
  *  http://rick-hightower.blogspot.com/2014/12/quick-guide-to-programming-services-in.html
  *  http://rick-hightower.blogspot.com/2015/01/quick-start-qbit-programming.html
  *  http://rick-hightower.blogspot.com/2015/01/high-speed-soa.html
  *  http://rick-hightower.blogspot.com/2015/02/qbit-event-bus.html

 ******************************************************************************/

package io.advantageous.qbit.service.impl;

import io.advantageous.qbit.Factory;
import io.advantageous.qbit.QBit;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.bindings.MethodBinding;
import io.advantageous.qbit.message.Response;
import io.advantageous.qbit.spi.RegisterBoonWithQBit;
import io.advantageous.qbit.util.MultiMap;
import io.advantageous.qbit.util.MultiMapImpl;
import org.boon.Lists;
import org.boon.Pair;
import org.boon.Str;
import org.boon.core.reflection.MethodAccess;
import org.junit.Test;

import java.util.Collection;
import java.util.Map;

import static org.boon.Boon.puts;
import static org.boon.Exceptions.die;

/**
 * Created by Richard on 9/26/14.
 */
public class BoonServiceMethodCallHandlerTest {


    static {
        RegisterBoonWithQBit.registerBoonWithQBit();

    }

    boolean methodCalled;
    boolean ok;

    @Test
    public void test() {
        BoonServiceMethodCallHandler impl = new BoonServiceMethodCallHandler(true);
        impl.init(new Foo(), "", "");

        final String address = impl.address();
        Str.equalsOrDie("/boo/baz", address);

        final Collection<String> addresses = impl.addresses();
        ok = addresses.contains("/boo/baz/baaah/pluck") || die(addresses);

        puts(addresses);

        final Map<String, Map<String, Pair<MethodBinding, MethodAccess>>> methodMap = impl.methodMap();

        for ( String key : methodMap.keySet() ) {
            puts(key);

        }

        final Factory factory = QBit.factory();

        methodCalled = false;
        impl.receiveMethodCall(factory.createMethodCallByAddress("/boo/baz/baaah/pluck", null, null, null));

        ok = methodCalled == true || die();


    }

    @Test
    public void testTwoBasicArgsNotDynamic() {

        BoonServiceMethodCallHandler impl = new BoonServiceMethodCallHandler(false);
        impl.init(new Foo(), "", "");

        final Factory factory = QBit.factory();

        methodCalled = false;

        impl.receiveMethodCall(factory.createMethodCallByAddress("/boo/baz/geoff/chandles/", null, Lists.list("1", 2), null));


        ok = methodCalled || die();

    }

    @Test
    public void testTwoBasicArgs() {

        BoonServiceMethodCallHandler impl = new BoonServiceMethodCallHandler(true);
        impl.init(new Foo(), "", "");

        final Factory factory = QBit.factory();

        methodCalled = false;

        impl.receiveMethodCall(factory.createMethodCallByAddress("/boo/baz/geoff/chandles/", null, Lists.list(1, 2), null));


        ok = methodCalled || die();

    }

    @Test
    public void testTwoBasicArgsInURIParams() {

        BoonServiceMethodCallHandler impl = new BoonServiceMethodCallHandler(true);
        impl.init(new Foo(), "", "");

        final Factory factory = QBit.factory();

        methodCalled = false;

        impl.receiveMethodCall(factory.createMethodCallByAddress("/boo/baz/geoff/chandles/twoargs/5/11/", null, null, null));


        ok = methodCalled || die();

    }

    @Test
    public void someMethod2() {

        BoonServiceMethodCallHandler impl = new BoonServiceMethodCallHandler(true);
        impl.init(new Foo(), null, null);

        final Factory factory = QBit.factory();

        methodCalled = false;

        MultiMap<String, String> params = new MultiMapImpl<>();
        params.put("methodName", "someMethod2");

        impl.receiveMethodCall(factory.createMethodCallByAddress("/boo/baz/beyondHereDontMatter", null,

                        Lists.list(1, 99), params));


        ok = methodCalled || die();

    }

    @Test
    public void someMethod3() {

        BoonServiceMethodCallHandler impl = new BoonServiceMethodCallHandler(true);
        impl.init(new Foo(), "/root", "/service");


        final String address = impl.address();
        Str.equalsOrDie("/root/service", address);

        final Collection<String> addresses = impl.addresses();
        ok = addresses.contains("/root/service/somemethod3") || die(addresses);

        final Factory factory = QBit.factory();

        methodCalled = false;


        impl.receiveMethodCall(factory.createMethodCallByAddress("/root/service/someMethod3/", null,

                Lists.list(1, 99), null));


        ok = methodCalled || die();

    }

    @Test
    public void someMethod4() {

        BoonServiceMethodCallHandler impl = new BoonServiceMethodCallHandler(true);
        impl.init(new Foo(), "/root", "/service");


        final String address = impl.address();
        Str.equalsOrDie("/root/service", address);

        final Collection<String> addresses = impl.addresses();
        ok = addresses.contains("/root/service/somemethod3") || die(addresses);

        final Factory factory = QBit.factory();

        methodCalled = false;


        final Response<Object> response = impl.receiveMethodCall(factory.createMethodCallByAddress("/root/service/someMethod3/", "returnAddress",

                        Lists.list(1, 99), null));

        ok = response != null || die();

        ok = methodCalled || die();

        //void does not return, its void.

    }

    @Test
    public void someMethod4NotDynamic() {

        BoonServiceMethodCallHandler impl = new BoonServiceMethodCallHandler(false);
        impl.init(new Foo(), "/root", "/service");


        final String address = impl.address();
        Str.equalsOrDie("/root/service", address);

        final Collection<String> addresses = impl.addresses();
        ok = addresses.contains("/root/service/somemethod3") || die(addresses);

        final Factory factory = QBit.factory();

        methodCalled = false;


        final Response<Object> response = impl.receiveMethodCall(factory.createMethodCallByAddress("/root/service/someMethod3/", "returnAddress",

                        Lists.list(1, 99), null));

        ok = response != null || die();

        ok = methodCalled || die();

        //void does not return, its void.

    }

    @RequestMapping("/boo/baz")
    class Foo {

        @RequestMapping("/baaah/pluck")
        public void foo() {

            methodCalled = true;
            puts("foo");
        }


        @RequestMapping("/geoff/chandles/twoargs/{0}/{1}/")
        public void geoff(String a, int b) {

            methodCalled = true;
            puts("geoff a", a, "b", b);
        }

        @RequestMapping("/geoff/chandles/")
        public void someMethod(String a, int b) {

            methodCalled = true;
            puts("geoff");
        }


        public void someMethod2(String a, int b) {

            methodCalled = true;
            puts("geoff", a, b);
        }


        public void someMethod3() {

            methodCalled = true;
        }
    }

}
