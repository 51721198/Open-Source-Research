/*
 * Copyright 2011 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package douyu.examples;

import java.io.PrintWriter;
import douyu.mvc.Controller;

@Controller
public class HelloWorld {
	// 用http://localhost:8080/douyu/examples/HelloWorld 或 http://localhost:8080/douyu/examples/HelloWorld.index 访问
	public void index(PrintWriter out) {
		out.println("index=> Hello Douyu World! Date: " + new java.util.Date());
	}

	// 用http://localhost:8080/douyu/examples/HelloWorld.hello访问
	public void hello(PrintWriter out) {
		out.println("hello=> Hello Douyu World! Date: " + new java.util.Date());
	}
}