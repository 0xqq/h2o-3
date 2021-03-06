package water.util;

import water.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Get zipped log directory data from a node.
 * The intent here is to return a binary blob with the data for saving as a file.
 * This is used as part of the "/3/Logs/download" REST API.
 */
public class GetLogsFromNode extends Iced {
  static final int MB = 1 << 20;
  static final int MAX_SIZE = 25 * MB;

  // Input
  /**
   * Node number to get logs from (starting at 0).
   */
  public int nodeidx;

  // Output
  /**
   * Byte array containing a zipped file with the entire log directory.
   */
  public byte[] bytes;

  /**
   * Do the work.
   */
  public void doIt() {
    if (nodeidx == -1) {
      GetLogsTask t = new GetLogsTask();
      t.doIt();
      bytes = t._bytes;
    }
    else {
      H2ONode node = H2O.CLOUD._memary[nodeidx];
      GetLogsTask t = new GetLogsTask();
      Log.trace("GetLogsTask starting to node " + nodeidx + "...");
      // Synchronous RPC call to get ticks from remote (possibly this) node.
      new RPC<>(node, t).call().get();
      Log.trace("GetLogsTask completed to node " + nodeidx);
      bytes = t._bytes;
    }
  }

  private static class GetLogsTask extends DTask<GetLogsTask> {
    private byte[] _bytes;

    public GetLogsTask() { super(H2O.MIN_HI_PRIORITY); _bytes = null; }

    public void doIt() {
      try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        String archiveRoot = String.format("h2ologs_node%d_%s_%d", H2O.SELF.index(),  H2O.SELF_ADDRESS.getHostAddress(), H2O.API_PORT);
        zipDir(Log.LOG_DIR, archiveRoot, baos, zos);
        zos.close();
        baos.close();
        _bytes = baos.toByteArray();
      }
      catch (Exception e) {
        _bytes = StringUtils.toBytes(e);
      }
    }

    @Override public void compute2() {
      doIt();
      tryComplete();
    }

    //here is the code for the method
    private void zipDir(String dir2zip, String pathInArchive, ByteArrayOutputStream baos, ZipOutputStream zos) throws IOException
    {
      try
      {
        //convert paths represented as strings into File instances
        File sourceDir = new File(dir2zip);
        File destinationDir = new File(pathInArchive);
        //get a listing of the directory content
        String[] dirList = sourceDir.list();
        byte[] readBuffer = new byte[4096]; 
        int bytesIn = 0;
        //loop through dirList, and zip the files
        for(int i=0; i<dirList.length; i++)
        {
          File sourceFile = new File(sourceDir, dirList[i]);
          File destinationFile = new File(destinationDir, dirList[i]);
          if(sourceFile.isDirectory())
          {
            //if the File object is a directory, call this
            zipDir(sourceFile.getPath(), destinationFile.getPath(), baos, zos);
            //loop again
            continue;
          }

          // In the Sparkling Water case, when running in the local-cluster configuration,
          // there are jar files in the log directory too.  Ignore them.
          if (sourceFile.toString().endsWith(".jar")) {
            continue;
          }

          //if we reached here, the File object f was not a directory
          //create a FileInputStream on top of f
          FileInputStream fis = new FileInputStream(sourceFile.getPath());
          // create a new zip entry
          ZipEntry anEntry = new ZipEntry(destinationFile.getPath());
          anEntry.setTime(sourceFile.lastModified());
          //place the zip entry in the ZipOutputStream object
          zos.putNextEntry(anEntry);
          //now write the content of the file to the ZipOutputStream

          boolean stopEarlyBecauseTooMuchData = false;
          while((bytesIn = fis.read(readBuffer)) != -1)
          {
            zos.write(readBuffer, 0, bytesIn);
            if (baos.size() > MAX_SIZE) {
              stopEarlyBecauseTooMuchData = true;
              break;
            }
          }
          //close the Stream
          fis.close();
          zos.closeEntry();

          if (stopEarlyBecauseTooMuchData) {
            Log.warn("GetLogsTask stopEarlyBecauseTooMuchData");
            break;
          }
        }
      }
      catch(Exception e) {
        Log.warn(e);
      }
    }
  }
}
