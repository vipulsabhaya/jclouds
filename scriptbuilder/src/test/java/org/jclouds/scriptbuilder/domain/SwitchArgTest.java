/**
 * Licensed to jclouds, Inc. (jclouds) under one or more
 * contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  jclouds licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jclouds.scriptbuilder.domain;

import static org.jclouds.scriptbuilder.domain.Statements.appendFile;
import static org.jclouds.scriptbuilder.domain.Statements.interpret;
import static org.jclouds.scriptbuilder.domain.Statements.newStatementList;
import static org.testng.Assert.assertEquals;

import java.util.Collections;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

/**
 * @author Adrian Cole
 */
@Test(groups = "unit")
public class SwitchArgTest {

   public void testSwitchArgUNIX() {
      assertEquals(new SwitchArg(1, ImmutableMap.of("0", newStatementList(appendFile(
               "{tmp}{fs}{uid}{fs}scripttest{fs}temp.txt", Collections.singleton("hello world")),
               interpret("echo hello zero{lf}")), "1", interpret("echo hello one{lf}"))).render(OsFamily.UNIX),
               "case $1 in\n0)\n   cat >> /tmp/$USER/scripttest/temp.txt <<'END_OF_FILE'\nhello world\nEND_OF_FILE\n   echo hello zero\n   ;;\n1)\n   echo hello one\n   ;;\nesac\n");
   }

   public void testSwitchArgWindows() {
      assertEquals(
               new SwitchArg(1, ImmutableMap.of("0", interpret("echo hello zero{lf}"), "1",
                        interpret("echo hello one{lf}"))).render(OsFamily.WINDOWS),
               "if not \"%1\" == \"0\" if not \"%1\" == \"1\" (\r\n   set EXCEPTION=bad argument: %1 not in 0 1\r\n   goto abort\r\n)\r\ngoto CASE_%1\r\n:CASE_0\r\n   echo hello zero\r\n   GOTO END_SWITCH\r\n:CASE_1\r\n   echo hello one\r\n   GOTO END_SWITCH\r\n:END_SWITCH\r\n");
   }
}
