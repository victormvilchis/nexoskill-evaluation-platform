package com.nexoskill.evaluation.forms.application;

import com.nexoskill.evaluation.forms.infrastructure.FormJpaEntity;
import com.nexoskill.evaluation.forms.infrastructure.FormRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class FormService {
 private final FormRepository repository;
 @PersistenceContext private EntityManager em;
 public FormService(FormRepository repository){this.repository=repository;}

 @Transactional(readOnly=true)
 public List<FormModels.FormSummary> list(){
   return repository.findAll().stream().sorted(Comparator.comparing(f->f.title.toLowerCase(Locale.ROOT)))
    .map(f->new FormModels.FormSummary(f.publicId,f.code,f.title,f.status,f.modeCode,f.passingScore,0,0,f.startsAt,f.endsAt,f.version)).toList();
 }

 @Transactional(readOnly=true)
 public FormModels.FormDetail get(String publicId){
   FormJpaEntity f=repository.findByPublicId(canonical(publicId)).orElseThrow(()->new NoSuchElementException("FORM_NOT_FOUND"));
   return detail(f,List.of());
 }

 @Transactional
 public FormModels.FormDetail create(FormModels.FormCommand c){
   validate(c,false);
   FormJpaEntity f=new FormJpaEntity();
   f.publicId=UUID.randomUUID().toString(); f.code=uniqueCode(c.title()); f.status="DRAFT"; f.createdAt=OffsetDateTime.now();
   apply(f,c); repository.saveAndFlush(f);
   return detail(f,List.of());
 }

 @Transactional
 public FormModels.FormDetail update(String publicId,FormModels.FormCommand c){
   validate(c,true);
   FormJpaEntity f=repository.findByPublicId(canonical(publicId)).orElseThrow(()->new NoSuchElementException("FORM_NOT_FOUND"));
   if(c.version()!=null && !Objects.equals(c.version(),f.version)) throw new IllegalStateException("FORM_CONCURRENTLY_MODIFIED");
   if("ARCHIVED".equals(f.status)) throw new IllegalStateException("FORM_ARCHIVED");
   apply(f,c); repository.saveAndFlush(f); return detail(f,List.of());
 }

 @Transactional
 public FormModels.FormDetail changeStatus(String publicId,String status){
   FormJpaEntity f=repository.findByPublicId(canonical(publicId)).orElseThrow(()->new NoSuchElementException("FORM_NOT_FOUND"));
   String target=status.toUpperCase(Locale.ROOT);
   if(!Set.of("DRAFT","ACTIVE","DISABLED","CLOSED","ARCHIVED").contains(target)) throw new IllegalArgumentException("FORM_STATUS_INVALID");
   if("ACTIVE".equals(target) && !f.acceptResponses.equals(1)) throw new IllegalStateException("FORM_RESPONSES_DISABLED");
   f.status=target; f.updatedAt=OffsetDateTime.now(); repository.saveAndFlush(f); return detail(f,List.of());
 }

 private void validate(FormModels.FormCommand c,boolean update){
   if(c==null||c.title()==null||c.title().trim().length()<3) throw new IllegalArgumentException("FORM_TITLE_REQUIRED");
   if(c.passingScore()==null||c.passingScore().signum()<0||c.passingScore().compareTo(new java.math.BigDecimal("100"))>0) throw new IllegalArgumentException("FORM_PASSING_SCORE_INVALID");
   if(c.startsAt()!=null&&c.endsAt()!=null&&!c.endsAt().isAfter(c.startsAt())) throw new IllegalArgumentException("FORM_DATES_INVALID");
   if(c.durationMinutes()!=null&&c.durationMinutes()<=0) throw new IllegalArgumentException("FORM_DURATION_INVALID");
   if(c.maxAttempts()!=null&&c.maxAttempts()<=0) throw new IllegalArgumentException("FORM_ATTEMPTS_INVALID");
 }
 private void apply(FormJpaEntity f,FormModels.FormCommand c){
   f.title=c.title().trim(); f.description=trim(c.description()); f.modeCode=upper(c.modeCode(),"ASSESSMENT"); f.passingScore=c.passingScore();
   f.maxAttempts=c.maxAttempts(); f.retryUntilPassed=b(c.retryUntilPassed()); f.acceptResponses=b(c.acceptResponses()); f.startsAt=c.startsAt(); f.endsAt=c.endsAt();
   f.durationMinutes=c.durationMinutes(); f.showResults=b(c.showResults()); f.showCorrectAnswers=b(c.showCorrectAnswers()); f.randomizeQuestions=b(c.randomizeQuestions());
   f.randomizeOptions=b(c.randomizeOptions()); f.showProgress=b(c.showProgress()); f.hideQuestionNumbers=b(c.hideQuestionNumbers()); f.allowSaveResume=b(c.allowSaveResume());
   f.oneActiveAttempt=b(c.oneActiveAttempt()); f.thankYouMessage=trim(c.thankYouMessage()); f.updatedAt=OffsetDateTime.now();
 }
 private FormModels.FormDetail detail(FormJpaEntity f,List<FormModels.SectionView> sections){return new FormModels.FormDetail(f.publicId,f.code,f.title,f.description,f.status,f.modeCode,f.passingScore,f.maxAttempts,f.retryUntilPassed==1,f.acceptResponses==1,f.startsAt,f.endsAt,f.durationMinutes,f.showResults==1,f.showCorrectAnswers==1,f.randomizeQuestions==1,f.randomizeOptions==1,f.showProgress==1,f.hideQuestionNumbers==1,f.allowSaveResume==1,f.oneActiveAttempt==1,f.thankYouMessage,f.version,sections);}
 private String uniqueCode(String title){String base=Normalizer.normalize(title,Normalizer.Form.NFD).replaceAll("\\p{M}","").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+","_").replaceAll("^_+|_+$",""); if(base.isBlank())base="FORM"; base=base.substring(0,Math.min(base.length(),60)); String c=base; int i=2; while(repository.existsByCode(c))c=base+"_"+(i++); return c;}
 private static String canonical(String id){return UUID.fromString(id.trim()).toString();} private static String trim(String s){return s==null?null:s.trim();} private static String upper(String s,String d){return s==null||s.isBlank()?d:s.trim().toUpperCase(Locale.ROOT);} private static int b(boolean v){return v?1:0;}
}
