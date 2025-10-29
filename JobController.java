package org.example.jobscraperweb;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class JobController {
    @Autowired
    private JobRepository jobRepository;

    @GetMapping("/")
    public String home(@RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "source", required = false) String source,
                       @RequestParam(value = "page", required = false, defaultValue = "0") int page,
                       @RequestParam(value = "size", required = false, defaultValue = "12") int size,
                       @RequestParam(value = "sort", required = false, defaultValue = "id") String sortField,
                       @RequestParam(value = "dir", required = false, defaultValue = "desc") String direction,
                       Model model) {

        keyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        source = (source != null && !source.trim().isEmpty()) ? source.trim() : null;

        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortField));

        Page<Job> jobPage;

        if (keyword != null && source != null) {
            jobPage = jobRepository.searchJobsBySource(keyword, source, pageable);
        } else if (keyword != null) {
            jobPage = jobRepository.searchJobs(keyword, pageable);
        } else if (source != null) {
            jobPage = jobRepository.findBySourceIgnoreCase(source, pageable);
        } else {
            jobPage = jobRepository.findAll(pageable);
        }

        List<String> sources = jobRepository.findAll()
                .stream()
                .map(Job::getSource)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(Collectors.toList());

        model.addAttribute("jobs", jobPage.getContent());
        model.addAttribute("sources", sources);
        model.addAttribute("keyword", keyword);
        model.addAttribute("source", source);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("totalPages", jobPage.getTotalPages());
        model.addAttribute("totalItems", jobPage.getTotalElements());
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortDir", direction);

        return "index";
    }
}
