/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.infinispan.cli.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class StreamIOAdapter implements IOAdapter {

   @Override
   public boolean isInteractive() {
      return false;
   }

   @Override
   public void println(String s) throws IOException {
      System.out.println(s);
   }

   @Override
   public void error(String s) throws IOException {
      System.err.println(s);
   }

   @Override
   public String readln(String s) throws IOException {
      System.out.print(s);
      BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
      return br.readLine();
   }

   @Override
   public String secureReadln(String s) throws IOException {
      //FIXME implement me
      return readln(s);
   }

   @Override
   public int getWidth() {
      return 72;
   }

   @Override
   public void close() {
      //FIXME implement me
   }
}
