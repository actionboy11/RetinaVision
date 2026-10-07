package com.example.retinavision.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatientQualityPresenterTest {

    private final PatientQualityPresenter presenter = new PatientQualityPresenter();

    @Test
    void mapsKnownStatusesToPatientLanguage() {
        assertThat(presenter.present("CHECKING", null))
                .extracting(PatientQualityPresenter.Presentation::status)
                .isEqualTo(PatientQualityDisplayStatus.CHECKING);
        assertThat(presenter.present("PASS", null))
                .extracting(PatientQualityPresenter.Presentation::status)
                .isEqualTo(PatientQualityDisplayStatus.ACCEPTABLE);
        assertThat(presenter.present("FAIL", "BLUR_DETECTED"))
                .isEqualTo(new PatientQualityPresenter.Presentation(
                        PatientQualityDisplayStatus.REUPLOAD_RECOMMENDED, "图像较模糊，建议重新上传"));
        assertThat(presenter.present("WARNING", "FIELD_INCOMPLETE"))
                .isEqualTo(new PatientQualityPresenter.Presentation(
                        PatientQualityDisplayStatus.REUPLOAD_RECOMMENDED, "拍摄区域不完整，建议重新上传"));
    }

    @Test
    void neverEchoesUnknownTechnicalErrors() {
        String rawError = "java.net.ConnectException: /srv/patient/original.png";

        var presentation = presenter.present("ERROR", rawError);

        assertThat(presentation.status()).isEqualTo(PatientQualityDisplayStatus.UNAVAILABLE);
        assertThat(presentation.message())
                .isEqualTo("当前无法确认图像质量，请稍后重试或联系负责医生")
                .doesNotContain("ConnectException", "/srv", rawError);
    }
}
