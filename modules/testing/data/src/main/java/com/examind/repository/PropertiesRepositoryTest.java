/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 * Copyright 2014 Geomatys.
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
package com.examind.repository;

import java.util.ArrayList;
import java.util.List;

import org.constellation.repository.PropertyRepository;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

public class PropertiesRepositoryTest extends AbstractRepositoryTest {


    @Autowired
    private PropertyRepository propertyRepository;

    @Test
    public void all() {
        dump(propertyRepository.findAll());

        List<String> keys = new ArrayList<String>();
        keys.add("test");

        dump(propertyRepository.findIn(keys));


    }

    @Test
    public void getValue() {
        String value = propertyRepository.getValue("test.notfound.property", "blurp");
        Assert.assertEquals("Default value is not matching", "blurp", value);
    }

    @Test
    @Transactional()
    public void save() {
        propertyRepository.update("test", "value");
    }

    @Test
    @Transactional()
    public void delete() {
        propertyRepository.delete("test");
    }

}
