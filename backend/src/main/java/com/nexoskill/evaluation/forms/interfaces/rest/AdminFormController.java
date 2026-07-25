package com.nexoskill.evaluation.forms.interfaces.rest;
import com.nexoskill.evaluation.forms.application.FormModels;
import com.nexoskill.evaluation.forms.application.FormService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/admin/forms")
public class AdminFormController {
 private final FormService service; public AdminFormController(FormService service){this.service=service;}
 @GetMapping @PreAuthorize("hasAuthority('FORM_VIEW')") public List<FormModels.FormSummary> list(){return service.list();}
 @GetMapping("/{publicId}") @PreAuthorize("hasAuthority('FORM_VIEW')") public FormModels.FormDetail get(@PathVariable String publicId){return service.get(publicId);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('FORM_CREATE')") public FormModels.FormDetail create(@RequestBody FormModels.FormCommand command){return service.create(command);}
 @PutMapping("/{publicId}") @PreAuthorize("hasAuthority('FORM_UPDATE')") public FormModels.FormDetail update(@PathVariable String publicId,@RequestBody FormModels.FormCommand command){return service.update(publicId,command);}
 @PostMapping("/{publicId}/status/{status}") @PreAuthorize("hasAuthority('FORM_STATUS_CHANGE')") public FormModels.FormDetail status(@PathVariable String publicId,@PathVariable String status){return service.changeStatus(publicId,status);}
}
