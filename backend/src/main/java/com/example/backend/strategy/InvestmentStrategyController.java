package com.example.backend.strategy;

import static com.example.backend.strategy.InvestmentStrategyDtos.*;

import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/investment-strategies")
public class InvestmentStrategyController {
    private final InvestmentStrategyService service;
    public InvestmentStrategyController(InvestmentStrategyService service){this.service=service;}
    @GetMapping public StrategyPage list(@RequestParam(required=false)String keyword,@RequestParam(required=false)String strategyScope,@RequestParam(required=false)String categoryCode,@RequestParam(required=false)String status,@RequestParam(defaultValue="latestActivityAt")String sortField,@RequestParam(defaultValue="desc")String sortDirection,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.findAll(new StrategyFilter(keyword,strategyScope,categoryCode,status,sortField,sortDirection,Math.max(0,page),Math.min(100,Math.max(1,size))));}
    @GetMapping("/links") public List<StrategyLink> links(){return service.links();}
    @GetMapping("/{id}") public StrategyDetail detail(@PathVariable long id){return service.findById(id);}
    @PostMapping public ResponseEntity<StrategyDetail> create(@RequestBody StrategySaveRequest request){var result=service.create(request);return ResponseEntity.created(URI.create("/api/investment-strategies/"+result.strategyId())).body(result);}
    @PutMapping("/{id}") public StrategyDetail update(@PathVariable long id,@RequestBody StrategySaveRequest request){return service.update(id,request);}
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable long id){service.delete(id);return ResponseEntity.noContent().build();}
    @PostMapping("/{id}/posts") public StrategyPost createPost(@PathVariable long id,@RequestBody PostSaveRequest request){return service.createPost(id,request);}
    @PutMapping("/{id}/posts/{postId}") public StrategyPost updatePost(@PathVariable long id,@PathVariable long postId,@RequestBody PostSaveRequest request){return service.updatePost(id,postId,request);}
    @DeleteMapping("/{id}/posts/{postId}") public ResponseEntity<Void> deletePost(@PathVariable long id,@PathVariable long postId){service.deletePost(id,postId);return ResponseEntity.noContent().build();}
    @PostMapping("/{id}/history/{historyId}/restore") public StrategyDetail restore(@PathVariable long id,@PathVariable long historyId){return service.restore(id,historyId);}
    @PutMapping("/{id}/journals") public StrategyDetail replaceJournals(@PathVariable long id,@RequestBody List<Long> journalIds){return service.replaceJournals(id,journalIds);}
}
