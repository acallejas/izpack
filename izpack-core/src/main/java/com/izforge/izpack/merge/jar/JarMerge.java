package com.izforge.izpack.merge.jar;

import com.izforge.izpack.api.exception.MergeException;
import com.izforge.izpack.merge.AbstractMerge;
import com.izforge.izpack.util.FileUtil;
import com.izforge.izpack.util.IoHelper;
import org.apache.tools.zip.ZipOutputStream;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Jar files merger.
 *
 * @author Anthonin Bonnefoy
 */
public class JarMerge extends AbstractMerge
{
    private String jarPath;

    private String regexp;
    private String destination;


    /**
     * Create a new JarMerge with a destination
     *
     * @param resource
     * @param jarPath      Path to the jar to merge
     * @param mergeContent map linking outputstream to their content to avoir duplication
     */
    public JarMerge(URL resource, String jarPath, Map<OutputStream, List<String>> mergeContent)
    {
        this.jarPath = jarPath;
        this.mergeContent = mergeContent;
        destination = FileUtil.convertUrlToFilePath(resource).replaceAll(this.jarPath, "").replaceAll("file:", "").replaceAll("!/?", "").replaceAll("//", "/");

        StringBuilder builder = new StringBuilder().append(destination);

        if (destination.endsWith("/"))
        {
            builder.append("+(.*)");
        }
        else
        {
            builder.append("/*(.*)");
        }
        regexp = builder.toString();
    }

    /**
     * Create a new JarMerge with a destination
     *
     * @param jarPath       Path to the jar to merge
     * @param pathInsideJar Inside path of the jar to merge. Can be a package or a file. Needed to build the regexp
     * @param destination   Destination of the package
     * @param mergeContent  map linking outputstream to their content to avoir duplication
     */
    public JarMerge(String jarPath, String pathInsideJar, String destination, Map<OutputStream, List<String>> mergeContent)
    {
        this.jarPath = jarPath;
        this.destination = destination;
        this.mergeContent = mergeContent;
        StringBuilder builder = new StringBuilder().append(pathInsideJar);
        if (pathInsideJar.endsWith("/"))
        {
            builder.append("+(.*)");
        }
        else
        {
            builder.append("/*(.*)");
        }
        regexp = builder.toString();
    }


    public File find(FileFilter fileFilter)
    {
        try
        {
            ArrayList<String> fileNameInZip = getFileNameInZip();
            for (String fileName : fileNameInZip)
            {
                File file = new File(jarPath + "!/" + fileName);
                if (fileFilter.accept(file))
                {
                    return file;
                }
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        return null;
    }

    public List<File> recursivelyListFiles(FileFilter fileFilter)
    {
        try
        {
            ArrayList<String> fileNameInZip = getFileNameInZip();
            ArrayList<File> result = new ArrayList<File>();
            ArrayList<File> filteredResult = new ArrayList<File>();
            for (String fileName : fileNameInZip)
            {
                result.add(new File(jarPath + "!" + fileName));
            }
            for (File file : result)
            {
                if (fileFilter.accept(file))
                {
                    filteredResult.add(file);
                }
            }
            return filteredResult;
        }
        catch (IOException e)
        {
            throw new MergeException(e);
        }
    }

    public ArrayList<String> getFileNameInZip() throws IOException
    {
        ZipInputStream inputStream = new ZipInputStream(new FileInputStream(jarPath));
        ArrayList<String> arrayList = new ArrayList<String>();
        ZipEntry zipEntry;
        while ((zipEntry = inputStream.getNextEntry()) != null)
        {
            arrayList.add(zipEntry.getName());
        }
        return arrayList;
    }

    public void merge(java.util.zip.ZipOutputStream outputStream)
    {
        Pattern pattern = Pattern.compile(regexp);
        List<String> mergeList = getMergeList(outputStream);
        ZipEntry zentry;
        try
        {
            JarInputStream jarInputStream = new JarInputStream(new FileInputStream(new File(jarPath)));
            while ((zentry = jarInputStream.getNextEntry()) != null)
            {
                Matcher matcher = pattern.matcher(zentry.getName());
                if (matcher.matches())
                {
                    if (mergeList.contains(zentry.getName()))
                    {
                        continue;
                    }
                    mergeList.add(zentry.getName());

                    String matchFile = matcher.group(1);
                    StringBuilder dest = new StringBuilder(destination);
                    if (matchFile != null && matchFile.length() > 0)
                    {
                        if (dest.length() > 0 && dest.charAt(dest.length() - 1) != '/')
                        {
                            dest.append('/');
                        }
                        dest.append(matchFile);
                    }
                    IoHelper.copyStreamToJar(jarInputStream, outputStream, dest.toString().replaceAll("//", "/"), zentry.getTime());
                }

            }
            jarInputStream.close();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void merge(ZipOutputStream outJar)
    {
        Pattern pattern = Pattern.compile(regexp);
        List<String> mergeList = getMergeList(outJar);
        ZipEntry zentry;
        try
        {
            JarInputStream jarInputStream = new JarInputStream(new FileInputStream(new File(jarPath)));
            while ((zentry = jarInputStream.getNextEntry()) != null)
            {
                Matcher matcher = pattern.matcher(zentry.getName());
                if (matcher.matches())
                {
                    if (mergeList.contains(zentry.getName()))
                    {
                        continue;
                    }
                    mergeList.add(zentry.getName());
                    String matchFile = matcher.group(1);
                    StringBuilder dest = new StringBuilder(destination);
                    if (matchFile != null && matchFile.length() > 0)
                    {
                        if (dest.length() > 0 && dest.charAt(dest.length() - 1) != '/')
                        {
                            dest.append('/');
                        }
                        dest.append(matchFile);
                    }
                    IoHelper.copyStreamToJar(jarInputStream, outJar, dest.toString().replaceAll("//", "/"), zentry.getTime());
                }

            }
            jarInputStream.close();
        }
        catch (IOException e)
        {
            throw new MergeException(e);
        }
    }

    @Override
    public String toString()
    {
        return "JarMerge{" +
                "jarPath='" + jarPath + '\'' +
                ", regexp='" + regexp + '\'' +
                ", destination='" + destination + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        JarMerge jarMerge = (JarMerge) o;

        if (jarPath != null ? !jarPath.equals(jarMerge.jarPath) : jarMerge.jarPath != null)
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return jarPath != null ? jarPath.hashCode() : 0;
    }
}