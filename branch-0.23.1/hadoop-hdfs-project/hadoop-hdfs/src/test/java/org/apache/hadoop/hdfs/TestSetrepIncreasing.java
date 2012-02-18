/**
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
package org.apache.hadoop.hdfs;

import junit.framework.TestCase;
import java.io.*;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.hdfs.server.datanode.SimulatedFSDataset;

public class TestSetrepIncreasing extends TestCase {
  static void setrep(int fromREP, int toREP, boolean simulatedStorage) throws IOException {
    Configuration conf = new HdfsConfiguration();
    if (simulatedStorage) {
      conf.setBoolean(SimulatedFSDataset.CONFIG_PROPERTY_SIMULATED, true);
    }
    conf.set(DFSConfigKeys.DFS_REPLICATION_KEY, "" + fromREP);
    conf.setLong(DFSConfigKeys.DFS_BLOCKREPORT_INTERVAL_MSEC_KEY, 1000L);
    conf.set(DFSConfigKeys.DFS_NAMENODE_REPLICATION_PENDING_TIMEOUT_SEC_KEY, Integer.toString(2));
    MiniDFSCluster cluster = new MiniDFSCluster.Builder(conf).numDataNodes(10).build();
    FileSystem fs = cluster.getFileSystem();
    assertTrue("Not a HDFS: "+fs.getUri(), fs instanceof DistributedFileSystem);

    try {
      Path root = TestDFSShell.mkdir(fs, 
          new Path("/test/setrep" + fromREP + "-" + toREP));
      Path f = TestDFSShell.writeFile(fs, new Path(root, "foo"));
      
      // Verify setrep for changing replication
      {
        String[] args = {"-setrep", "-w", "" + toREP, "" + f};
        FsShell shell = new FsShell();
        shell.setConf(conf);
        try {
          assertEquals(0, shell.run(args));
        } catch (Exception e) {
          assertTrue("-setrep " + e, false);
        }
      }

      //get fs again since the old one may be closed
      fs = cluster.getFileSystem();
      FileStatus file = fs.getFileStatus(f);
      long len = file.getLen();
      for(BlockLocation locations : fs.getFileBlockLocations(file, 0, len)) {
        assertTrue(locations.getHosts().length == toREP);
      }
      TestDFSShell.show("done setrep waiting: " + root);
    } finally {
      try {fs.close();} catch (Exception e) {}
      cluster.shutdown();
    }
  }

  public void testSetrepIncreasing() throws IOException {
    setrep(3, 7, false);
  }
  public void testSetrepIncreasingSimulatedStorage() throws IOException {
    setrep(3, 7, true);
  }
}
