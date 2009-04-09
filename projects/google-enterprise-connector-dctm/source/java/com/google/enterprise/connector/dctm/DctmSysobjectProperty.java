// Copyright (C) 2006-2009 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.dctm;

import java.util.HashSet;
import java.util.Iterator;

import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Value;

public class DctmSysobjectProperty implements Property {
  private String name;

  private Iterator iter;

  public DctmSysobjectProperty(String name, HashSet hashSet) {
    this.name = name;
    this.iter = hashSet.iterator();
  }

  public String getName() throws RepositoryException {
    return name;
  }

  public Value nextValue() throws RepositoryException {
    if (iter.hasNext()) {
      return (Value) iter.next();
    }
    return null;
  }
}