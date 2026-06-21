package com.example.retinavision.service.impl;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.service.ReportPdfRenderer;
import com.example.retinavision.service.ReportPdfDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

@Component
public class JdkReportPdfRenderer implements ReportPdfRenderer {
    private final Path fontPath;
    public JdkReportPdfRenderer(@Value("${retina.report.font-path:C:/Windows/Fonts/simhei.ttf}") String path) {
        this.fontPath = Paths.get(path).toAbsolutePath().normalize();
    }
    @Override public void render(ReportPdfDocument document, Path target) {
        if (!Files.isRegularFile(fontPath)) throw new BaseException(ErrorMessageSignal.FILE_STORAGE_ERROR, "报告中文字体未配置或不存在");
        try {
            Font font = Font.createFont(Font.TRUETYPE_FONT, fontPath.toFile()).deriveFont(18f);
            BufferedImage page = new BufferedImage(595, 842, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = page.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0,0,595,842); g.setColor(Color.BLACK); g.setFont(font);
            int y=55; g.drawString("RetinaVision AI 辅助分析报告",40,y); y+=35;
            g.setFont(font.deriveFont(13f)); g.drawString("AI 辅助分析，不等同于独立医学诊断",40,y); y+=30;
            String text=(document.content()==null?"{}":document.content()).replaceAll("[\\r\\n]+"," ");
            for(int i=0;i<text.length()&&y<390;i+=42,y+=22) g.drawString(text.substring(i,Math.min(text.length(),i+42)),40,y);
            drawImage(g, document.originalImage(), 35, 430, 165, 250, "原始眼底图", font);
            drawImage(g, document.aiMask(), 215, 430, 165, 250, "AI 分割图", font);
            drawImage(g, document.correctedMask(), 395, 430, 165, 250, "人工修正图", font);
            g.dispose(); writePdf(page,target);
        } catch (FontFormatException|IOException e) { throw new BaseException(ErrorMessageSignal.FILE_STORAGE_ERROR,"PDF 报告生成失败"); }
    }
    private void drawImage(Graphics2D g, Path path, int x, int y, int w, int h, String title, Font font) throws IOException {
        g.setFont(font.deriveFont(12f)); g.drawString(title, x, y - 8);
        if (path == null || !Files.isRegularFile(path)) { g.drawRect(x,y,w,h); g.drawString("无",x+70,y+125); return; }
        BufferedImage image=javax.imageio.ImageIO.read(path.toFile()); if(image==null)return;
        double scale=Math.min((double)w/image.getWidth(),(double)h/image.getHeight());int dw=(int)(image.getWidth()*scale),dh=(int)(image.getHeight()*scale);
        g.drawImage(image,x+(w-dw)/2,y+(h-dh)/2,dw,dh,null);
    }
    private void writePdf(BufferedImage image,Path target) throws IOException {
        byte[] rgb=new byte[image.getWidth()*image.getHeight()*3]; int p=0;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int c=image.getRGB(x,y);rgb[p++]=(byte)(c>>16);rgb[p++]=(byte)(c>>8);rgb[p++]=(byte)c;}
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();try(DeflaterOutputStream z=new DeflaterOutputStream(compressed)){z.write(rgb);}
        ByteArrayOutputStream out=new ByteArrayOutputStream();List<Integer> offsets=new ArrayList<>();ascii(out,"%PDF-1.4\n");
        obj(out,offsets,1,"<< /Type /Catalog /Pages 2 0 R >>");obj(out,offsets,2,"<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        obj(out,offsets,3,"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>");
        offsets.add(out.size());ascii(out,"4 0 obj\n<< /Type /XObject /Subtype /Image /Width 595 /Height 842 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length "+compressed.size()+" >>\nstream\n");out.write(compressed.toByteArray());ascii(out,"\nendstream\nendobj\n");
        byte[] command="q 595 0 0 842 0 0 cm /Im0 Do Q".getBytes(StandardCharsets.US_ASCII);offsets.add(out.size());ascii(out,"5 0 obj\n<< /Length "+command.length+" >>\nstream\n");out.write(command);ascii(out,"\nendstream\nendobj\n");
        int xref=out.size();ascii(out,"xref\n0 6\n0000000000 65535 f \n");for(int offset:offsets)ascii(out,String.format("%010d 00000 n \n",offset));ascii(out,"trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n"+xref+"\n%%EOF\n");
        Files.createDirectories(target.getParent());Files.write(target,out.toByteArray());
    }
    private void obj(ByteArrayOutputStream out,List<Integer> offsets,int id,String body)throws IOException{offsets.add(out.size());ascii(out,id+" 0 obj\n"+body+"\nendobj\n");}
    private void ascii(ByteArrayOutputStream out,String s)throws IOException{out.write(s.getBytes(StandardCharsets.US_ASCII));}
}
