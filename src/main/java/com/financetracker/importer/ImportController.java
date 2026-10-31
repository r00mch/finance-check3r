package com.financetracker.importer;

import com.financetracker.common.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/import")
@Tag(name = "Import")
public class ImportController {

    private final ImportService service;

    ImportController(ImportService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import a CSV bank statement into an account",
            description = "Header row required: date, amount (+ optional description, category). "
                    + "Rows already imported earlier are skipped, so re-uploading a file is safe.")
    ImportResult importCsv(@PathVariable Long accountId, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("The file is empty");
        }
        try {
            return service.importStatement(accountId, file.getBytes());
        } catch (IOException e) {
            throw new BadRequestException("The uploaded file could not be read");
        }
    }
}
