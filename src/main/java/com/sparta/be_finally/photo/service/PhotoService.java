package com.sparta.be_finally.photo.service;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.collect.Lists;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.sparta.be_finally.config.S3.AwsS3Service;
import com.sparta.be_finally.config.dto.PrivateResponseBody;
import com.sparta.be_finally.config.errorcode.CommonStatusCode;
import com.sparta.be_finally.config.util.SecurityUtil;
import com.sparta.be_finally.config.validator.Validator;
import com.sparta.be_finally.photo.dto.CompletePhotoRequestDto;
import com.sparta.be_finally.photo.dto.FrameResponseDto;
import com.sparta.be_finally.photo.dto.PhotoRequestDto;
import com.sparta.be_finally.photo.entity.Photo;
import com.sparta.be_finally.photo.repository.PhotoRepository;
import com.sparta.be_finally.room.entity.Room;
import com.sparta.be_finally.room.repository.RoomRepository;
import com.sparta.be_finally.user.entity.User;
import io.openvidu.java.client.OpenVidu;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PhotoService {
    private final RoomRepository roomRepository;
    @Value("${cloud.aws.s3.bucket}" )
    private String bucket;
    private final AwsS3Service awsS3Service;
    private final Validator validator;
    private final PhotoRepository photoRepository;
    private final AmazonS3Client amazonS3Client;
    private OpenVidu openVidu;

    // QR 색상
    private static int backgroundColor = 0xFF000002;
    private static int paintColor = 0xFFF8F9FA;

    // OpenVidu 서버가 수신하는 URL
    @Value("${openvidu.url}" )
    private String OPENVIDU_URL;

    // OpenVidu 서버와 공유되는 비밀
    @Value("${openvidu.secret}" )
    private String OPENVIDU_SECRET;

    @PostConstruct
    public OpenVidu openVidu() {
        return openVidu = new OpenVidu(OPENVIDU_URL, OPENVIDU_SECRET);
    }

    // 사진 촬영 준비
    @Transactional
    public FrameResponseDto photoShoot(Long roomId) {
        User user = SecurityUtil.getCurrentUser();
        // 1. roomId 존재 여부 확인
        Room room = validator.existsRoom(roomId);

        // 2. 입장한 방 - 선택한 프레임 번호
        //int frameNum = room.getFrame();

        return new FrameResponseDto(room.getFrame(), room.getFrameUrl());
    }

    // 찍은 사진 S3 저장
    @Transactional
    public PrivateResponseBody photoShootSave(Long roomId, PhotoRequestDto photoRequestDto) {
        // 1. roomId 존재 여부 확인
        Room room = validator.existsRoom(roomId);

        // 2. Photo 테이블 - room_id 에서 촬영한 사진 조회
        //    사진을 한 컷 이상 찍은 상태 : isExist 에 정보 저장 됨
        //    photo_one 촬영 한 상태 : isExist = null
        Photo photo = photoRepository.findByRoomId(roomId).orElse(null);

        // 3. photoRequestDto 에 있는 파일 S3에 업로드
        if (photoRequestDto.getPhoto_1()!=null || photoRequestDto.getPhoto_2() !=null|| photoRequestDto.getPhoto_3() !=null|| photoRequestDto.getPhoto_4()!=null) {
            if (photo.getPhotoOne() == null && photoRequestDto.getPhoto_1() != null && !photoRequestDto.getPhoto_1().getContentType().isEmpty()) {
                String photo_one_imgUrl = awsS3Service.uploadFile(photoRequestDto.getPhoto_1(), room.getId());
                photo.photo_one_update(photo_one_imgUrl);
                return new PrivateResponseBody(CommonStatusCode.SHOOT_PHOTO_GET);


            } else if (photo.getPhotoTwo() == null && photoRequestDto.getPhoto_2() != null && !photoRequestDto.getPhoto_2().getContentType().isEmpty()) {
                String photo_two_imgUrl = awsS3Service.uploadFile(photoRequestDto.getPhoto_2(), room.getId());
                photo.photo_two_update(photo_two_imgUrl);
                return new PrivateResponseBody(CommonStatusCode.SHOOT_PHOTO_GET);


            } else if (photo.getPhotoThree() == null && photoRequestDto.getPhoto_3() != null && !photoRequestDto.getPhoto_3().getContentType().isEmpty()) {
                String photo_three_imgUrl = awsS3Service.uploadFile(photoRequestDto.getPhoto_3(), room.getId());
                photo.photo_three_update(photo_three_imgUrl);
                return new PrivateResponseBody(CommonStatusCode.SHOOT_PHOTO_GET);

            } else if (photo.getPhotoFour() == null && photoRequestDto.getPhoto_4() != null && !photoRequestDto.getPhoto_4().getContentType().isEmpty()) {
                String photo_four_imgUrl = awsS3Service.uploadFile(photoRequestDto.getPhoto_4(), room.getId());
                photo.photo_four_update(photo_four_imgUrl);
                return new PrivateResponseBody(CommonStatusCode.SHOOT_PHOTO_GET);

            } else {
                return new PrivateResponseBody(CommonStatusCode.FAIL_SAVE_PHOTO);
            }
        }
        return new PrivateResponseBody(CommonStatusCode.SHOOT_PHOTO_FAIL);
    }

    @Transactional(readOnly = true)
    public PrivateResponseBody photoGet(Long roomId) {

        Room room = validator.existsRoom(roomId);

        ObjectListing objectListing = amazonS3Client.listObjects(bucket, "photo/"+room.getId() + "/");
        List<S3ObjectSummary> s3ObjectSummaries = objectListing.getObjectSummaries();

        List<String> imgUrlList = Lists.newArrayList();

        for (S3ObjectSummary s3Object : s3ObjectSummaries) {
            String imgKey = s3Object.getKey();
            String imgUrl = amazonS3Client.getResourceUrl(bucket, imgKey);
            imgUrlList.add(imgUrl);
        }
        //return imgUrlList;
        return new PrivateResponseBody(CommonStatusCode.SHOOT_PHOTO_GET, imgUrlList, new FrameResponseDto(room.getFrame(), room.getFrameUrl()), room.getId());
    }

    // 완성 사진 저장
    @Transactional
    public PrivateResponseBody completePhotoSave(Long roomId, CompletePhotoRequestDto completePhotoRequestDto) {
        // 1. roomId 존재 여부 확인
        Room room = validator.existsRoom(roomId);

        // 2. Room 테이블 - 완성 이미지 저장
        String completePhoto = awsS3Service.uploadFile(completePhotoRequestDto.getCompletePhoto(), room.getId());
        photoRepository.updateCompletePhoto(completePhoto, roomId);

        // 3. QR코드 생성
        String qrCode = createQr(roomId); // base64 인코딩
        System.out.println("qrCode : " + qrCode);
        photoRepository.saveQrCode(qrCode, roomId);

        if(photoRepository.findByRoomIdAndCompletePhotoNull(roomId) != null) {
            return new PrivateResponseBody(CommonStatusCode.COMPLETE_PHOTO_SUCCESS);
        } else {
            return new PrivateResponseBody(CommonStatusCode.COMPLETE_PHOTO_FAIL);
        }
    }

    @Transactional
    public PrivateResponseBody returnQr(Long roomId) {
        String qrcode = photoRepository.findByRoomIdAndQrCode(roomId);
        if (qrcode != null) {

            return new PrivateResponseBody(CommonStatusCode.CREATE_QRCODE, qrcode);
        } else {
            return new PrivateResponseBody(CommonStatusCode.FAIL_QRCODE);
        }
    }

    // QR코드 생성
    private String createQr(Long roomId) {
        byte[] image = new byte[0];
        String url = photoRepository.createQrPhotoUrl(roomId);
//        String url = completePhotoRequestDto.getCompletePhoto().toString();
        try {
            image = PhotoService.getQRCodeImage(url, 250, 250);
        } catch (WriterException | IOException e) {
            e.printStackTrace();
        }

        System.out.println("url:" + url);
        System.out.println("image:" + image);

        // 이거 가져올 때 쓰기
        String qrcode = Base64.getEncoder().encodeToString(image);

        return qrcode;
    }

    // QR이미지 생성
    private static byte[] getQRCodeImage(String url, int width, int height) throws WriterException, IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, width, height);

        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageConfig matrixToImageConfig = new MatrixToImageConfig(backgroundColor, paintColor);

        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream, matrixToImageConfig);
        byte[] pngData = pngOutputStream.toByteArray();

        return pngData;
    }

    public String decodeQR(byte[] qrCodeBytes) {
        try {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(qrCodeBytes);
            BufferedImage bufferedImage = ImageIO.read(byteArrayInputStream);
            BufferedImageLuminanceSource bufferedImageLuminanceSource = new BufferedImageLuminanceSource(bufferedImage);
            HybridBinarizer hybridBinarizer = new HybridBinarizer(bufferedImageLuminanceSource);
            BinaryBitmap binaryBitmap = new BinaryBitmap(hybridBinarizer);
            MultiFormatReader multiFormatReader = new MultiFormatReader();
            Result result = multiFormatReader.decode(binaryBitmap);
            return result.getText();
        } catch (NotFoundException e) {
            return "QR Code Not Found In This Image!";
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }







}

