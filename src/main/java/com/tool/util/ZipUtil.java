package com.tool.util;

import com.sun.media.jai.codec.ImageCodec;
import com.sun.media.jai.codec.ImageEncoder;
import com.sun.media.jai.codec.JPEGEncodeParam;
import com.sun.media.jai.codec.TIFFEncodeParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipFile;
import org.apache.tools.zip.ZipOutputStream;
import org.dom4j.Document;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

import javax.imageio.ImageIO;
import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static javax.print.attribute.ResolutionSyntax.DPI;

/**
 * 影像上传所用功能工具
 * Created by za-linboyi on 2017/9/12.
 */
@Slf4j
public class ZipUtil {

    private static BASE64Encoder encoder = new BASE64Encoder();
    private static BASE64Decoder decoder = new BASE64Decoder();
    /**
     * 压缩文件夹
     * @param filesDirPath  目标文件  E:/a/b
     * @param zipFilePath  存放压缩文件的路径名 E:/a/b.zip
     * @param deleteFile 是否删除文件夹及目录
     * @return boolean  是否压缩成功
     * */
    public static boolean doZip(String filesDirPath, String zipFilePath,boolean deleteFile) {
        return doZip(new File(filesDirPath), zipFilePath,deleteFile);
    }

    private static boolean doZip(File inputFile, String zipFileName,boolean deleteFile) {
        ZipOutputStream out = null;
        try {
            out = new ZipOutputStream(new FileOutputStream(zipFileName));
            boolean result = doZip(out, inputFile, "");
            return result;
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
            return false;
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        } finally {
            try {
                out.close();
                if (deleteFile) {
                    deleteDir(inputFile);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                return false;
            }
        }
    }

    private static boolean doZip(ZipOutputStream out, File f, String base) {
        try {
            if (f.isDirectory()) {
                File[] fl = f.listFiles();
                if (fl.length == 0) {
                    out.putNextEntry(new org.apache.tools.zip.ZipEntry(base + "/"));
                }
                for (int i = 0; i < fl.length; i++) {
                    if (StringUtils.isEmpty(base)) {
                        doZip(out, fl[i], fl[i].getName());
                    }else{
                        doZip(out, fl[i], base + "/" + fl[i].getName());
                    }

                }
            } else {
                out.putNextEntry(new org.apache.tools.zip.ZipEntry(base));
                FileInputStream in = new FileInputStream(f);
                int b;
                while ((b = in.read()) != -1) {
                    out.write(b);
                }
                in.close();
            }
            return true;
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * 解压zip文件
     * @param zipFilePath  压缩文件路径名  E:/b/a.zip
     * @param base  解压文件存放路径  E:/b/c
     * @param deleteFile 是否删除压缩包
     * @return boolean 是否解压成功
     * */
    public static boolean unZip(String zipFilePath, String base, boolean deleteFile) {
        try {
            File file = new File(zipFilePath);
            if (!file.exists()) {
                throw new RuntimeException("解压文件不存在!");
            }
            ZipFile zipFile = new ZipFile(file);
            Enumeration e = zipFile.getEntries();
            while (e.hasMoreElements()) {
                ZipEntry zipEntry = (ZipEntry) e.nextElement();
                if (zipEntry.isDirectory()) {
                    String name = zipEntry.getName();
                    name = name.substring(0, name.length() - 1);
                    File f = new File(base+ "/" + name);
                    f.mkdirs();
                } else {
                    File f = new File(base + zipEntry.getName());
                    f.getParentFile().mkdirs();
                    f.createNewFile();
                    InputStream is = zipFile.getInputStream(zipEntry);
                    FileOutputStream fos = new FileOutputStream(f);
                    int length = 0;
                    byte[] b = new byte[1024];
                    while ((length = is.read(b, 0, 1024)) != -1) {
                        fos.write(b, 0, length);
                    }
                    is.close();
                    fos.close();
                }
            }
            if (zipFile != null) {
                zipFile.close();
            }
            if (deleteFile) {
                file.deleteOnExit();
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    /**
     * 递归删除目录下的所有文件及子目录下所有文件
     * @param dir 将要删除的文件目录
     * @return boolean
     */
    private static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();//递归删除目录中的子目录下
            for (int i=0; i<children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        // 目录此时为空，可以删除
        return dir.delete();
    }

    /**
     * 将base64字符解码保存文件
     * @param base64Code
     * @param targetPath
     * @throws Exception
     */

    public static void decoderBase64File(String base64Code, String targetPath)
            throws Exception {
        byte[] buffer = new BASE64Decoder().decodeBuffer(base64Code);
        File file=new File(targetPath);
        if(!file.getParentFile().exists()){
            file.getParentFile().mkdirs();
        }
        FileOutputStream out = new FileOutputStream(targetPath);
        out.write(buffer);
        out.close();
    }

    /**
     * doc -> 生成XML 文件
     * @return
     */
    public static boolean createXml(Document doc,String filePath){
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setEncoding("UTF-8");//设置xml文档的编码为utf-8
        try {
            OutputStream out = new FileOutputStream(filePath+"/busi.xml");
            Writer wr = new OutputStreamWriter(out, "UTF-8");
            //创建一个dom4j创建xml的对象
            XMLWriter writer = new XMLWriter(wr, format);
            //调用write方法将doc文档写到指定路径
            writer.write(doc);
            writer.close();
            log.info("生成XML文件成功");
        } catch (IOException e) {
            log.info("生成XML文件失败");
            e.printStackTrace();
        }

        return true;
    }

    /**
     * 输入流转换文件
     * @param ins
     * @param file
     */
    public static void inputstreamtofile(InputStream ins,File file){
        log.info("输入流转换文件开始");
        try {
            if(!file.getParentFile().exists()){
                file.getParentFile().mkdirs();
            }
            FileOutputStream os = new FileOutputStream(file);
            int bytesRead = 0;
            byte[] buffer = new byte[1024];
            while ((bytesRead = ins.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.close();
            ins.close();
            log.info("保存图片文件成功");
        }catch (Exception e){
            log.info("保存图片文件失败");
        }finally {
            if (ins != null)
                try {
                    ins.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }

    }

    /**
     *
     * @param fileIn 输入文件
     * @param fileOut 输出文件
     * @return
     * @throws Exception
     */
    public static void changePdfToTiff(File fileIn,File fileOut) throws Exception {

        long begin = System.currentTimeMillis();
        PDDocument doc = null;
        try {
            if(!fileOut.getParentFile().exists()){
                fileOut.getParentFile().mkdirs();
            }
            OutputStream os = new FileOutputStream(fileOut);
            if(!fileIn.getParentFile().exists()){
                fileIn.getParentFile().mkdirs();
            }
            InputStream ins =new FileInputStream(fileIn);
            doc = PDDocument.load(ins);
            int pageCount = doc.getNumberOfPages();
            PDFRenderer renderer = new PDFRenderer(doc); // 根据PDDocument对象创建pdf渲染器

            List<PlanarImage> piList = new ArrayList<PlanarImage>(pageCount - 1);
            for (int i = 0 + 1; i < pageCount; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, DPI, ImageType.RGB);

                PlanarImage pimg = JAI.create("mosaic", image);
                piList.add(pimg);
            }

            TIFFEncodeParam param = new TIFFEncodeParam();// 创建tiff编码参数类
            param.setCompression(TIFFEncodeParam.COMPRESSION_DEFLATE);// 压缩参数
            param.setExtraImages(piList.iterator());// 设置图片的迭代器
            BufferedImage fimg = renderer.renderImageWithDPI(0, DPI, ImageType.RGB);
            PlanarImage fpi = JAI.create("mosaic", fimg); // 通过JAI的create()方法实例化jai的图片对象

            ImageEncoder enc = ImageCodec.createImageEncoder("tiff", os, param);
            enc.encode(fpi);// 指定第一个进行编码的jai图片对象,并将输出写入到与此
            ins.close();
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (doc != null)
                    doc.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        long end = System.currentTimeMillis();

        log.info("pdf 转换 tiif 开始时间：" + begin + "结束时间;" + end + "耗时：" + (end - begin));
    }

    /**
     * oss文件流转tiff
     * @param ins oss文件流
     * @param fileOut 输出文件
     * @return
     * @throws Exception
     */
    public static void changePdfToTiffWithOss(InputStream ins,File fileOut) throws Exception {

        long begin = System.currentTimeMillis();
        PDDocument doc = null;
        try {
            if(!fileOut.getParentFile().exists()){
                fileOut.getParentFile().mkdirs();
            }
            OutputStream os = new FileOutputStream(fileOut);
            doc = PDDocument.load(ins);
            int pageCount = doc.getNumberOfPages();
            PDFRenderer renderer = new PDFRenderer(doc); // 根据PDDocument对象创建pdf渲染器

            List<PlanarImage> piList = new ArrayList<PlanarImage>(pageCount - 1);
            for (int i = 0 + 1; i < pageCount; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, DPI, ImageType.RGB);

                PlanarImage pimg = JAI.create("mosaic", image);
                piList.add(pimg);
            }

            TIFFEncodeParam param = new TIFFEncodeParam();// 创建tiff编码参数类
            param.setCompression(TIFFEncodeParam.COMPRESSION_DEFLATE);// 压缩参数
            param.setExtraImages(piList.iterator());// 设置图片的迭代器
            BufferedImage fimg = renderer.renderImageWithDPI(0, DPI, ImageType.RGB);
            PlanarImage fpi = JAI.create("mosaic", fimg); // 通过JAI的create()方法实例化jai的图片对象

            ImageEncoder enc = ImageCodec.createImageEncoder("tiff", os, param);
            enc.encode(fpi);// 指定第一个进行编码的jai图片对象,并将输出写入到与此
            ins.close();
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (doc != null)
                    doc.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        long end = System.currentTimeMillis();

        log.info("pdf 转换 tiif 开始时间：" + begin + "结束时间;" + end + "耗时：" + (end - begin));
    }

    /**
     * 对一个文件获取md5值
     * @return md5串
     */
    public static String getMD5(File file) {
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        MessageDigest digest = null;
        FileInputStream in = null;
        byte buffer[] = new byte[1024];
        int len;
        try {
            digest = MessageDigest.getInstance("MD5");
            in = new FileInputStream(file);
            while ((len = in.read(buffer, 0, 1024)) != -1) {
                digest.update(buffer, 0, len);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (null !=in) {
                try {
                    in.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
        byte[] md5Bytes = digest.digest();
        StringBuffer hexValue = new StringBuffer();
        for (int i = 0; i < md5Bytes.length; i++){
            int val = ((int) md5Bytes[i]) & 0xff;
            if (val < 16)
                hexValue.append("0");
            hexValue.append(Integer.toHexString(val));
        }
        return hexValue.toString().toUpperCase();
//        BigInteger bigInt = new BigInteger(1, digest.digest());
//        return bigInt.toString(16);
    }

    /**
     * 文件转byte[]
     * @param file
     * @return
     */
    public static byte[] getByteFromFile(File file){
        FileInputStream fin =null;
        ByteArrayOutputStream out =null;
        try {
            fin = new FileInputStream(file);
            out = new ByteArrayOutputStream();
            byte[] bytes = new byte[1024];
            int read;
            while ((read = fin.read(bytes)) > 0) {
                out.write(bytes, 0, read);
            }
            bytes = out.toByteArray();
            return bytes;
        }catch (Exception e){
            log.error("文件转byte 失败",e);
        }finally {
            if (fin !=null) {
                try {
                    fin.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
            if (out !=null) {
                try {
                    out.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
        return null;

    }

    /**
     * Tiff文件转jpg （转换后删除原文件）
     * @param inputFilePath 原文件路径
     * @param outPutFilePath 目标文件路径
     */
    public static void changeTiffToJpg(String inputFilePath, String outPutFilePath){
        try {
            File fileIn=new File(inputFilePath);
            if(!fileIn.getParentFile().exists()){
                fileIn.getParentFile().mkdirs();
            }
            File fileOut=new File(outPutFilePath);
            if(!fileOut.getParentFile().exists()){
                fileOut.getParentFile().mkdirs();
            }
            RenderedOp src2 = JAI.create("fileload", inputFilePath);
            OutputStream os2 = new FileOutputStream(outPutFilePath);
            JPEGEncodeParam param2 = new JPEGEncodeParam();
            //指定格式类型，jpg 属于 JPEG 类型
            ImageEncoder enc2 = ImageCodec.createImageEncoder("JPEG", os2, param2);
            enc2.encode(src2);
            os2.close();
            fileIn.delete();
        }catch (Exception e){
            log.error("Tiff文件转换Jpg 失败",e);
        }

    }

    /**
     * Png文件转jpg （转换后删除原文件）
     * @param inputFilePath 原文件路径
     * @param outPutFilePath 目标文件路径
     */
    public static void changePngToJpg (String inputFilePath, String outPutFilePath){
        try {
            File fileIn=new File(inputFilePath);
            if(!fileIn.getParentFile().exists()){
                fileIn.getParentFile().mkdirs();
            }
            File fileOut=new File(outPutFilePath);
            if(!fileOut.getParentFile().exists()){
                fileOut.getParentFile().mkdirs();
            }
            //read image file
            BufferedImage bufferedImage = ImageIO.read(fileIn);

            // create a blank, RGB, same width and height, and a white background
            BufferedImage newBufferedImage = new BufferedImage(bufferedImage.getWidth(),
                    bufferedImage.getHeight(), BufferedImage.TYPE_INT_RGB);
            newBufferedImage.createGraphics().drawImage(bufferedImage, 0, 0, Color.WHITE, null);

            // write to jpeg file
            ImageIO.write(newBufferedImage, "jpg", fileOut);
            fileIn.delete();
        }catch (Exception e){
            log.error("Png文件转换Jpg 失败",e);
        }
    }

    /**
     *  将PDF转换成base64编码
     *  1.使用BufferedInputStream和FileInputStream从File指定的文件中读取内容；
     *  2.然后建立写入到ByteArrayOutputStream底层输出流对象的缓冲输出流BufferedOutputStream
     *  3.底层输出流转换成字节数组，然后由BASE64Encoder的对象对流进行编码
     * */
   public static String getPDFBinary(File file) {
        FileInputStream fin = null;
        BufferedInputStream bin = null;
        ByteArrayOutputStream baos = null;
        BufferedOutputStream bout = null;
        try {
            // 建立读取文件的文件输出流
            fin = new FileInputStream(file);
            // 在文件输出流上安装节点流（更大效率读取）
            bin = new BufferedInputStream(fin);
            // 创建一个新的 byte 数组输出流，它具有指定大小的缓冲区容量
            baos = new ByteArrayOutputStream();
            // 创建一个新的缓冲输出流，以将数据写入指定的底层输出流
            bout = new BufferedOutputStream(baos);
            byte[] buffer = new byte[1024];
            int len = bin.read(buffer);
            while (len != -1) {
                bout.write(buffer, 0, len);
                len = bin.read(buffer);
            }
            // 刷新此输出流并强制写出所有缓冲的输出字节，必须这行代码，否则有可能有问题
            bout.flush();
            byte[] bytes = baos.toByteArray();
            // sun公司的API
            return encoder.encodeBuffer(bytes).trim();
            // apache公司的API
            // return Base64.encodeBase64String(bytes);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                fin.close();
                bin.close();
                // 关闭 ByteArrayOutputStream 无效。此类中的方法在关闭此流后仍可被调用，而不会产生任何 IOException
                // IOException
                // baos.close();
                bout.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 将base64编码转换成PDF
     *
     * @param base64sString
     *            1.使用BASE64Decoder对编码的字符串解码成字节数组
     *            2.使用底层输入流ByteArrayInputStream对象从字节数组中获取数据；
     *            3.建立从底层输入流中读取数据的BufferedInputStream缓冲输出流对象；
     *            4.使用BufferedOutputStream和FileOutputSteam输出数据到指定的文件中
     */
   public static void base64StringToPDF(String base64sString) {
        BufferedInputStream bin = null;
        FileOutputStream fout = null;
        BufferedOutputStream bout = null;
        try {
            // 将base64编码的字符串解码成字节数组
            byte[] bytes = decoder.decodeBuffer(base64sString);
            // apache公司的API
            // byte[] bytes = Base64.decodeBase64(base64sString);
            // 创建一个将bytes作为其缓冲区的ByteArrayInputStream对象
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            // 创建从底层输入流中读取数据的缓冲输入流对象
            bin = new BufferedInputStream(bais);
            // 指定输出的文件
            File file = new File("D:\\test.pdf");
            // 创建到指定文件的输出流
            fout = new FileOutputStream(file);
            // 为文件输出流对接缓冲输出流对象
            bout = new BufferedOutputStream(fout);

            byte[] buffers = new byte[1024];
            int len = bin.read(buffers);
            while (len != -1) {
                bout.write(buffers, 0, len);
                len = bin.read(buffers);
            }
            // 刷新此输出流并强制写出所有缓冲的输出字节，必须这行代码，否则有可能有问题
            bout.flush();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                bin.close();
                fout.close();
                bout.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }



}
