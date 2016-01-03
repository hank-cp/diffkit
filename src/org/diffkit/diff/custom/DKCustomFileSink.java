/**
 * Copyright 2010-2011 Joseph Panico
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.diffkit.diff.custom;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ClassUtils;
import org.diffkit.common.DKRuntime;
import org.diffkit.diff.engine.DKColumnModel;
import org.diffkit.diff.engine.DKContext;
import org.diffkit.diff.engine.DKSource;
import org.diffkit.diff.engine.DKTableComparison;
import org.diffkit.diff.sns.DKDBSource;
import org.diffkit.diff.sns.DKWriterSink;
import org.diffkit.util.DKFileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Map;

/**
 * @author jpanico
 */
public class DKCustomFileSink extends DKWriterSink {

   private final File _file;
   private final Writer _writer;
   private final Logger _log = LoggerFactory.getLogger(this.getClass());

   private int _consistentCount;
   private int _failedConsistentCount;

   public DKCustomFileSink(String filePath_) {
      super(null);
      File previousFile = new File(filePath_);
      if (previousFile.exists()) {
         previousFile.renameTo(new File(filePath_ + "." + System.currentTimeMillis()));
      }

      _file = new File(filePath_);
      try {
         _writer = new BufferedWriter(new FileWriter(_file));
      } catch (IOException e) {
         throw new RuntimeException("Open diff file to write failed", e);
      }
      _log.debug("_file->{}", _file);
   }

   public String getSummary(DKContext context_) {
      StringBuilder builder = new StringBuilder();
      String startTimeString = DKRuntime.getInstance().getIsTest() ? "xxx"
              : new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(context_.getDiffStartTime());
      builder.append(String.format("\"diff_start\":\"%s\",", startTimeString));
      String elapsedTimeString = DKRuntime.getInstance().getIsTest() ? "xxx"
              : context_.getElapsedTimeString();
      builder.append(String.format("\"diff_elapsed_time\":\"%s\",", elapsedTimeString));
      builder.append(String.format("\"rows\":%d,", context_._rowStep-1));
      builder.append(String.format("\"diff_rows\":%d,", getRowDiffCount()));
      builder.append(String.format("\"column_rows\":%d,", getColumnDiffCount()));
      builder.append(String.format("\"consistentCount\":%d,", _consistentCount));
      builder.append(String.format("\"failedUpdateConsistentCount\":%d", _failedConsistentCount));
      return builder.toString();
   }

   @Override
   public Kind getKind() {
      return Kind.FILE;
   }

   public File getFile() {
      return _file;
   }

   @Override
   public void open(DKContext context_) throws IOException {
      this.init(_writer, DKCustomFormatter.getInstance());
      _writer.write("[\n");
      super.open(context_);
   }

   public String toString() {
      if (DKRuntime.getInstance().getIsTest())
         return _file.getName();
      return String.format("%s@%x[%s]", ClassUtils.getShortClassName(this.getClass()),
         System.identityHashCode(this), _file.getPath());
   }

   @Override
   public void close(DKContext context_) throws IOException {
      _writer.write(String.format("{%s}\n]", getSummary(_context)));
      super.close(context_);
   }

   @Override
   public void onRowConsistent(DKSource lhs, Object[] lhsData, DKSource rhs, Object[] rhsData) {
      DKDBSource dbSource = (DKDBSource) lhs;
      try {
         _consistentCount += 1;
         dbSource.getDatabase().executeUpdate("UPDATE `proc_stock_in_task` SET `STATUS`=3 WHERE `TASK_NUMBER`="+lhsData[0]);
      } catch (SQLException e) {
         _failedConsistentCount += 1;
         _log.error("Update lhs table failed.", e);
      }
   }
}
